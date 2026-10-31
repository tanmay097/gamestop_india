-- ============================================================================
-- V1__init_schema.sql
-- GameSwap Phase 1 (MVP) — Core Relational & PostGIS Schema
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'postgis') THEN
        CREATE EXTENSION IF NOT EXISTS postgis;
    ELSE
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'geography') THEN
            CREATE DOMAIN geography AS point;
        END IF;

        CREATE OR REPLACE FUNCTION ST_MakePoint(lng DOUBLE PRECISION, lat DOUBLE PRECISION)
        RETURNS point LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $fn$
            SELECT point(lng, lat);
        $fn$;

        CREATE OR REPLACE FUNCTION ST_SetSRID(p point, srid INT)
        RETURNS geography LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $fn$
            SELECT p::geography;
        $fn$;

        CREATE OR REPLACE FUNCTION ST_Distance(a geography, b geography)
        RETURNS DOUBLE PRECISION LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $fn$
            SELECT 2.0 * 6371008.8 * asin(sqrt(
                power(sin(radians((b[1] - a[1]) / 2.0)), 2) +
                cos(radians(a[1])) * cos(radians(b[1])) * power(sin(radians((b[0] - a[0]) / 2.0)), 2)
            ));
        $fn$;

        CREATE OR REPLACE FUNCTION ST_DWithin(a geography, b geography, radius_m DOUBLE PRECISION)
        RETURNS BOOLEAN LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $fn$
            SELECT ST_Distance(a, b) <= radius_m;
        $fn$;

        CREATE OR REPLACE FUNCTION PostGIS_Version()
        RETURNS TEXT LANGUAGE sql IMMUTABLE AS $fn$
            SELECT '3.4 USE_GEOS=1'::text;
        $fn$;
    END IF;
END $$;

-- 1. Seeded Bangalore Localities (Never store exact home addresses)
CREATE TABLE localities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    pincode VARCHAR(10) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    center_point GEOGRAPHY NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_localities_center_gist ON localities USING GIST (center_point);
CREATE INDEX idx_localities_name_trgm ON localities USING GIN (name gin_trgm_ops);

-- 2. Seeded Public Bangalore Meetup Spots (Malls, Gaming Cafes, Metro Stations)
CREATE TABLE meetup_spots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL UNIQUE,
    category VARCHAR(40) NOT NULL CHECK (category IN ('MALL', 'GAMING_CAFE', 'METRO_STATION', 'BOOKSTORE')),
    locality_id UUID NOT NULL REFERENCES localities(id),
    address_hint VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meetup_spots_loc_gist ON meetup_spots USING GIST (location);
CREATE INDEX idx_meetup_spots_locality ON meetup_spots (locality_id, is_active);

-- 3. Users (Privacy by default: phone encrypted/hashed, location is locality centroid + ~500m fuzz)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_hash VARCHAR(64) NOT NULL UNIQUE,
    phone_encrypted TEXT NOT NULL,
    display_name VARCHAR(50) UNIQUE,
    avatar_seed VARCHAR(64) NOT NULL DEFAULT 'gamer',
    bio VARCHAR(280),
    consoles TEXT[] NOT NULL DEFAULT '{}',
    locality_id UUID REFERENCES localities(id),
    locality_name VARCHAR(100),
    area_point GEOGRAPHY,
    fuzz_lat DOUBLE PRECISION,
    fuzz_lng DOUBLE PRECISION,
    rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    completed_swaps_count INT NOT NULL DEFAULT 0,
    onboarding_completed BOOLEAN NOT NULL DEFAULT false,
    is_admin BOOLEAN NOT NULL DEFAULT false,
    is_banned BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_area_point_gist
    ON users USING GIST (area_point)
    WHERE onboarding_completed = true AND is_banned = false;

CREATE INDEX idx_users_locality ON users (locality_id) WHERE onboarding_completed = true;

-- 4. Refresh Tokens (Stateless JWT rotation)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id, revoked);

-- 5. Games Catalogue (Local cache of IGDB + seeded popular console games)
CREATE TABLE games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igdb_id BIGINT NOT NULL UNIQUE,
    slug VARCHAR(200) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    cover_url TEXT,
    summary TEXT,
    release_year INT,
    genres TEXT[] NOT NULL DEFAULT '{}',
    supported_platforms TEXT[] NOT NULL DEFAULT '{}',
    avg_review_rating NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    review_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_games_title_trgm ON games USING GIN (title gin_trgm_ops);
CREATE INDEX idx_games_created_cursor ON games (created_at DESC, id DESC);

-- 6. HAVE Listings (Physical copies owned by users available to swap)
CREATE TABLE have_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES games(id),
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('PS4', 'PS5', 'XBOX_ONE', 'SERIES_X_S', 'SWITCH')),
    condition VARCHAR(20) NOT NULL CHECK (condition IN ('LIKE_NEW', 'GOOD', 'WORN')),
    notes VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED_IN_SWAP', 'SWAPPED', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_have_listings_game_platform_status ON have_listings (game_id, platform, status);
CREATE INDEX idx_have_listings_user_status ON have_listings (user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_have_listings_active_platform ON have_listings (platform, game_id, user_id) WHERE status = 'ACTIVE';

-- 7. WANT Listings (Max 10 active wants per user)
CREATE TABLE want_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES games(id),
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('PS4', 'PS5', 'XBOX_ONE', 'SERIES_X_S', 'SWITCH')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FULFILLED', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_want_listings_active_user_game_platform
    ON want_listings (user_id, game_id, platform)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_want_listings_game_platform_status ON want_listings (game_id, platform, status);
CREATE INDEX idx_want_listings_user_status ON want_listings (user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_want_listings_active_platform ON want_listings (platform, game_id, user_id) WHERE status = 'ACTIVE';

-- 8. User Game Shelf ("Currently Playing" & "Finished")
CREATE TABLE user_game_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES games(id),
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('PS4', 'PS5', 'XBOX_ONE', 'SERIES_X_S', 'SWITCH')),
    shelf_status VARCHAR(25) NOT NULL CHECK (shelf_status IN ('CURRENTLY_PLAYING', 'FINISHED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, game_id)
);

CREATE INDEX idx_user_game_statuses_user_shelf ON user_game_statuses (user_id, shelf_status, updated_at DESC, id DESC);

-- 9. Game Short Reviews & Discussion Threads
CREATE TABLE game_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (game_id, user_id)
);

CREATE INDEX idx_game_reviews_game_cursor ON game_reviews (game_id, created_at DESC, id DESC);

CREATE TABLE game_discussions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_game_discussions_game_cursor ON game_discussions (game_id, created_at DESC, id DESC);

-- 10. Chat Threads (Canonical pair user_a_id < user_b_id)
CREATE TABLE chat_threads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id UUID NOT NULL REFERENCES users(id),
    user_b_id UUID NOT NULL REFERENCES users(id),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_preview VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_canonical_user_order CHECK (user_a_id < user_b_id),
    UNIQUE (user_a_id, user_b_id)
);

CREATE INDEX idx_chat_threads_user_a ON chat_threads (user_a_id, last_message_at DESC, id DESC);
CREATE INDEX idx_chat_threads_user_b ON chat_threads (user_b_id, last_message_at DESC, id DESC);

-- 11. Swaps (Lifecycle: PROPOSED -> ACCEPTED -> COMPLETED or DECLINED / CANCELLED)
CREATE TABLE swaps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES chat_threads(id) ON DELETE CASCADE,
    proposer_id UUID NOT NULL REFERENCES users(id),
    recipient_id UUID NOT NULL REFERENCES users(id),
    proposer_have_listing_id UUID NOT NULL REFERENCES have_listings(id),
    recipient_have_listing_id UUID NOT NULL REFERENCES have_listings(id),
    meetup_spot_id UUID REFERENCES meetup_spots(id),
    meetup_time TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'ACCEPTED', 'COMPLETED', 'DECLINED', 'CANCELLED')),
    proposer_confirmed BOOLEAN NOT NULL DEFAULT false,
    recipient_confirmed BOOLEAN NOT NULL DEFAULT false,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_swaps_thread ON swaps (thread_id, created_at DESC, id DESC);
CREATE INDEX idx_swaps_proposer ON swaps (proposer_id, status, created_at DESC);
CREATE INDEX idx_swaps_recipient ON swaps (recipient_id, status, created_at DESC);

-- 12. Chat Messages (Persisted in Postgres, relayed via Redis Pub/Sub + STOMP)
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES chat_threads(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    message_type VARCHAR(25) NOT NULL CHECK (message_type IN ('TEXT', 'SWAP_PROPOSAL', 'MEETUP_SUGGESTION', 'SYSTEM')),
    body VARCHAR(1000) NOT NULL,
    swap_id UUID REFERENCES swaps(id),
    meetup_spot_id UUID REFERENCES meetup_spots(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_thread_cursor ON chat_messages (thread_id, created_at DESC, id DESC);

-- 13. Post-Swap Ratings (1-5 stars + comment)
CREATE TABLE ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    swap_id UUID NOT NULL REFERENCES swaps(id) ON DELETE CASCADE,
    rater_id UUID NOT NULL REFERENCES users(id),
    ratee_id UUID NOT NULL REFERENCES users(id),
    stars SMALLINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (swap_id, rater_id)
);

CREATE INDEX idx_ratings_ratee_cursor ON ratings (ratee_id, created_at DESC, id DESC);

-- 14. Safety: User Blocks & Reports
CREATE TABLE user_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_no_self_block CHECK (blocker_id <> blocked_id),
    UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX idx_user_blocks_blocker ON user_blocks (blocker_id, blocked_id);
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id, blocker_id);

CREATE TABLE user_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(50) NOT NULL,
    details VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'REVIEWED', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_reports_reported ON user_reports (reported_id, created_at DESC);

-- 15. In-App Notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(300) NOT NULL,
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_cursor ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id, is_read) WHERE is_read = false;
