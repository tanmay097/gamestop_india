# GameSwap — Scaling Notes (10k → 100k → 1M Users)

GameSwap Phase 1 is architected as a **stateless, 12-factor modular monolith** (`auth`, `users`, `games`, `listings`, `matching`, `chat`, `swaps`, `ratings`, `safety`) backed by **PostgreSQL + PostGIS** and **Redis**. Because every domain module communicates via clean service contracts, uses **UUID primary keys**, enforces **cursor-based keyset pagination** (no `OFFSET` scans), and uses **Redis Pub/Sub** for WebSocket fanout, the system can scale from local Bangalore launch to **1,000,000+ users** across India without a rewrite.

---

## Stage 1: 10,000 Users (~1,500 DAU, Single Metro — Bangalore)

At 10k users, a single primary PostgreSQL instance and a single Redis instance handle the load comfortably with minor operational tuning:

1. **Horizontal Backend Auto-Scaling (2–4 Stateless Nodes)**:
   - Because authentication is stateless JWT and WebSocket chat fanout uses `ChatPubSubRelay` over Redis Pub/Sub (`gameswap:chat:events`), run 2–4 Spring Boot instances behind an ALB/NGINX load balancer with sticky WebSocket upgrades.
2. **Connection Pooling with PgBouncer**:
   - Place **PgBouncer** (transaction pooling mode) in front of PostgreSQL so 4+ backend pods with 20 HikariCP connections each do not exhaust Postgres backends.
3. **CDN for Static PWA Assets & Game Covers**:
   - Put CloudFront or Cloudflare in front of the Next.js PWA static assets (`/_next/static/*`, service worker, icons) and proxy/cache IGDB cover images at the edge.
4. **SMS Provider Cutover**:
   - Switch `OTP_PROVIDER=msg91` (or `twilio`) by implementing the `SmsOtpProvider` SPI bean without touching `AuthService`.

---

## Stage 2: 100,000 Users (~15,000–25,000 DAU, Multi-City Expansion)

At 100k users, read-heavy spatial matching, map pin queries, and real-time chat traffic become the primary bottlenecks:

1. **PostgreSQL Read Replicas + Spring `AbstractRoutingDataSource`**:
   - Route read-only queries (`MatchingService.findMatches`, `MatchingService.findMapPins`, `GamesService.searchGames`, `UsersService.getPublicProfile`) to **1–2 read replicas** (`@Transactional(readOnly = true)`), keeping the primary exclusively for writes (OTP refresh tokens, swap state transitions, new listings, chat inserts).
2. **Declarative Table Partitioning on `chat_messages` and `notifications`**:
   - `chat_messages` grows fastest. Partition `chat_messages` by time range (`PARTITION BY RANGE (created_at)` monthly via `pg_partman`) or hash-partition by `thread_id` so the `(thread_id, created_at DESC, id DESC)` index stays in RAM.
   - Partition `notifications` by `created_at` monthly and drop/archive read notifications older than 90 days.
3. **Splitting Out the `chat` Module into a Dedicated WebSocket Service**:
   - WebSocket connections are memory- and file-descriptor-bound, whereas `/api/v1/matches` is CPU/DB-bound.
   - Extract `com.gameswap.chat` into a standalone Spring Boot deployment (`gameswap-chat-service`) from the same Gradle monorepo. Route `/ws/**` and `/api/v1/chats/**` at the API Gateway/ALB to `gameswap-chat-service`, while all other `/api/v1/**` traffic goes to `gameswap-core-api`.
4. **Async Event Queue (Kafka / RabbitMQ / AWS SQS)**:
   - Instead of synchronous cache invalidation and notification inserts inside request transactions, publish domain events (`ListingMutatedEvent`, `SwapStateChangedEvent`, `RatingSubmittedEvent`) to a message queue for asynchronous match-cache warming and push notification delivery.

---

## Stage 3: 1,000,000 Users (~150,000+ DAU, Pan-India Scale)

At 1M users (~2M+ active `have_listings`, ~5M+ `want_listings`, tens of thousands of concurrent WebSocket connections):

1. **Precomputed Match Materialization & Inverted Game-Locality Index**:
   - Instead of computing 2-way and 1-way joins on-the-fly on every cache miss, maintain an event-driven materialized match table or Redis Sorted Set (`ZSET` keyed by `user:{id}:matches` scored by `matchTier * 1e9 - distanceMeters`).
   - Whenever a user in locality `L` adds a `HAVE(gameId, platform)` or `WANT(gameId, platform)` listing, a background worker queries only users within 25 km of `L` who have the complementary `WANT`/`HAVE` and updates their top matches asynchronously.
2. **Redis Cluster + Dedicated Redis Roles**:
   - Split Redis into three isolated clusters:
     - **Redis Cache Cluster** (`allkeys-lru` eviction) for match pages, map pins, and IGDB catalogue cache.
     - **Redis State Store** (`noeviction` + AOF persistence) for OTP challenges and sliding-window rate limit counters.
     - **Redis Pub/Sub / Streams Cluster** (or **NATS / Kafka**) for cross-node WebSocket chat message routing.
3. **Database Sharding / Geographic Partitioning**:
   - Because GameSwap is strictly **hyperlocal** (swaps only occur within a city/metro radius <= 25 km), `users`, `have_listings`, and `want_listings` partition naturally by `metro_region_id` (e.g., `BLR`, `BOM`, `DEL`, `HYD`, `MAA`) using Citus or declarative list partitioning without cross-shard spatial joins.
4. **Full-Text & Vector Game Search Offload**:
   - Offload `games` and `game_discussions` search from PostgreSQL `pg_trgm` to OpenSearch / Typesense for typo-tolerant instant search and trending local game analytics.

