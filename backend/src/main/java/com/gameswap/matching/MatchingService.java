package com.gameswap.matching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameswap.common.api.CursorCodec;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.config.GameSwapProperties;
import com.gameswap.common.error.ApiException;
import com.gameswap.common.ratelimit.RedisStoreService;
import com.gameswap.matching.MatchingDtos.*;
import com.gameswap.users.ConsolePlatform;
import com.gameswap.users.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final JdbcClient jdbcClient;
    private final RedisStoreService redisStore;
    private final GameSwapProperties properties;
    private final ObjectMapper objectMapper;

    public MatchingService(
        JdbcClient jdbcClient,
        RedisStoreService redisStore,
        GameSwapProperties properties,
        ObjectMapper objectMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.redisStore = redisStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private record CandidateRow(
        UUID userId,
        String displayName,
        String avatarSeed,
        String localityName,
        List<String> consoles,
        BigDecimal ratingAvg,
        int ratingCount,
        int completedSwapsCount,
        int matchTier,
        double distanceMeters
    ) {}

    public CursorPage<MatchCardDto> findMatches(
        UUID userId,
        Double radiusKmParam,
        String platformParam,
        String cursor,
        Integer limit
    ) {
        ensureUserOnboarded(userId);

        double radiusKm = clampRadiusKm(radiusKmParam);
        double radiusMeters = radiusKm * 1000.0;
        String platform = (platformParam != null && !platformParam.isBlank())
            ? ConsolePlatform.normalizeAndValidate(platformParam)
            : null;
        int pageSize = CursorCodec.clampLimit(limit, 15, 50);

        String cacheKey = String.format(
            "matches:%s:%.1f:%s:%s:%d",
            userId, radiusKm, platform != null ? platform : "ALL", cursor != null ? cursor : "FIRST", pageSize
        );

        String cachedJson = redisStore.get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<CursorPage<MatchCardDto>>() {});
            } catch (Exception e) {
                log.debug("Failed to deserialize cached matches, recomputing", e);
            }
        }

        CursorCodec.MatchCursor decodedCursor = CursorCodec.decodeMatch(cursor);

        StringBuilder sql = new StringBuilder("""
            WITH me AS (
                SELECT id, area_point FROM users WHERE id = :meId
            ),
            scored_candidates AS (
                SELECT u.id AS user_id,
                       u.display_name,
                       u.avatar_seed,
                       u.locality_name,
                       u.consoles,
                       u.rating_avg,
                       u.rating_count,
                       u.completed_swaps_count,
                       ROUND(ST_Distance(u.area_point, me.area_point)::numeric, 2)::double precision AS distance_m,
                       EXISTS (
                           SELECT 1
                           FROM have_listings th
                           JOIN want_listings mw ON mw.game_id = th.game_id AND mw.platform = th.platform
                           WHERE th.user_id = u.id
                             AND th.status = 'ACTIVE'
                             AND mw.user_id = :meId
                             AND mw.status = 'ACTIVE'
                             AND (:platform IS NULL OR th.platform = :platform)
                       ) AS has_what_i_want,
                       EXISTS (
                           SELECT 1
                           FROM want_listings tw
                           JOIN have_listings mh ON mh.game_id = tw.game_id AND mh.platform = tw.platform
                           WHERE tw.user_id = u.id
                             AND tw.status = 'ACTIVE'
                             AND mh.user_id = :meId
                             AND mh.status = 'ACTIVE'
                             AND (:platform IS NULL OR tw.platform = :platform)
                       ) AS wants_what_i_have
                FROM users u
                CROSS JOIN me
                WHERE u.id <> :meId
                  AND u.onboarding_completed = true
                  AND u.is_banned = false
                  AND u.area_point IS NOT NULL
                  AND ST_DWithin(u.area_point, me.area_point, :radiusMeters)
                  AND NOT EXISTS (
                      SELECT 1 FROM user_blocks b
                      WHERE (b.blocker_id = :meId AND b.blocked_id = u.id)
                         OR (b.blocker_id = u.id AND b.blocked_id = :meId)
                  )
            ),
            ranked AS (
                SELECT user_id,
                       display_name,
                       avatar_seed,
                       locality_name,
                       consoles,
                       rating_avg,
                       rating_count,
                       completed_swaps_count,
                       distance_m,
                       CASE
                           WHEN has_what_i_want AND wants_what_i_have THEN 2
                           ELSE 1
                       END AS match_tier
                FROM scored_candidates
                WHERE has_what_i_want OR wants_what_i_have
            )
            SELECT *
            FROM ranked
            """);

        if (decodedCursor != null) {
            sql.append("""
                WHERE (match_tier < :cursorTier)
                   OR (match_tier = :cursorTier AND distance_m > :cursorDist)
                   OR (match_tier = :cursorTier AND distance_m = :cursorDist AND user_id > :cursorUserId)
                """);
        }

        sql.append(" ORDER BY match_tier DESC, distance_m ASC, user_id ASC LIMIT :limit ");

        var spec = jdbcClient.sql(sql.toString())
            .param("meId", userId)
            .param("platform", platform)
            .param("radiusMeters", radiusMeters)
            .param("limit", pageSize + 1);

        if (decodedCursor != null) {
            spec = spec
                .param("cursorTier", decodedCursor.matchTier())
                .param("cursorDist", decodedCursor.distanceMeters())
                .param("cursorUserId", decodedCursor.userId());
        }

        List<CandidateRow> rows = spec.query((rs, rowNum) -> new CandidateRow(
            rs.getObject("user_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("avatar_seed"),
            rs.getString("locality_name"),
            UsersService.extractStringArray(rs, "consoles"),
            rs.getBigDecimal("rating_avg"),
            rs.getInt("rating_count"),
            rs.getInt("completed_swaps_count"),
            rs.getInt("match_tier"),
            rs.getDouble("distance_m")
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<CandidateRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        List<MatchCardDto> cards = enrichMatchCards(userId, pageRows, platform);
        String nextCursor = hasMore
            ? CursorCodec.encodeMatch(
                pageRows.getLast().matchTier(),
                pageRows.getLast().distanceMeters(),
                pageRows.getLast().userId()
            )
            : null;

        CursorPage<MatchCardDto> result = CursorPage.of(cards, nextCursor, hasMore);
        try {
            redisStore.set(
                cacheKey,
                objectMapper.writeValueAsString(result),
                Duration.ofSeconds(properties.matching().cacheTtlSeconds())
            );
        } catch (Exception e) {
            log.debug("Failed to cache match results", e);
        }

        return result;
    }

    public CursorPage<MapPinDto> findMapPins(
        UUID userId,
        Double radiusKmParam,
        UUID gameId,
        String platformParam,
        String cursor,
        Integer limit
    ) {
        ensureUserOnboarded(userId);

        double radiusKm = clampRadiusKm(radiusKmParam);
        double radiusMeters = radiusKm * 1000.0;
        String platform = (platformParam != null && !platformParam.isBlank())
            ? ConsolePlatform.normalizeAndValidate(platformParam)
            : null;
        int pageSize = CursorCodec.clampLimit(limit, 40, 100);
        CursorCodec.DistanceCursor decoded = CursorCodec.decodeDistance(cursor);

        StringBuilder sql = new StringBuilder("""
            WITH me AS (
                SELECT id, area_point FROM users WHERE id = :meId
            ),
            pins AS (
                SELECT h.id AS listing_id,
                       u.id AS user_id,
                       u.display_name,
                       u.avatar_seed,
                       u.locality_name,
                       u.rating_avg,
                       h.game_id,
                       g.title AS game_title,
                       g.cover_url AS game_cover_url,
                       h.platform,
                       h.condition,
                       u.fuzz_lat,
                       u.fuzz_lng,
                       h.created_at,
                       ROUND(ST_Distance(u.area_point, me.area_point)::numeric, 2)::double precision AS distance_m,
                       CASE
                           WHEN EXISTS (
                               SELECT 1 FROM want_listings mw
                               WHERE mw.user_id = :meId AND mw.status = 'ACTIVE'
                                 AND mw.game_id = h.game_id AND mw.platform = h.platform
                           ) AND EXISTS (
                               SELECT 1 FROM want_listings tw
                               JOIN have_listings mh ON mh.game_id = tw.game_id AND mh.platform = tw.platform
                               WHERE tw.user_id = u.id AND tw.status = 'ACTIVE'
                                 AND mh.user_id = :meId AND mh.status = 'ACTIVE'
                           ) THEN 2
                           WHEN EXISTS (
                               SELECT 1 FROM want_listings mw
                               WHERE mw.user_id = :meId AND mw.status = 'ACTIVE'
                                 AND mw.game_id = h.game_id AND mw.platform = h.platform
                           ) THEN 1
                           ELSE 0
                       END AS match_tier
                FROM have_listings h
                JOIN users u ON u.id = h.user_id
                JOIN games g ON g.id = h.game_id
                CROSS JOIN me
                WHERE h.status = 'ACTIVE'
                  AND u.id <> :meId
                  AND u.onboarding_completed = true
                  AND u.is_banned = false
                  AND u.area_point IS NOT NULL
                  AND ST_DWithin(u.area_point, me.area_point, :radiusMeters)
                  AND NOT EXISTS (
                      SELECT 1 FROM user_blocks b
                      WHERE (b.blocker_id = :meId AND b.blocked_id = u.id)
                         OR (b.blocker_id = u.id AND b.blocked_id = :meId)
                  )
            """);

        if (gameId != null) {
            sql.append(" AND h.game_id = :gameId ");
        }
        if (platform != null) {
            sql.append(" AND h.platform = :platform ");
        }
        sql.append(" ) SELECT * FROM pins ");
        if (decoded != null) {
            sql.append(" WHERE (distance_m > :cursorDist) OR (distance_m = :cursorDist AND listing_id > :cursorId) ");
        }
        sql.append(" ORDER BY distance_m ASC, listing_id ASC LIMIT :limit ");

        var spec = jdbcClient.sql(sql.toString())
            .param("meId", userId)
            .param("radiusMeters", radiusMeters)
            .param("limit", pageSize + 1);

        if (gameId != null) {
            spec = spec.param("gameId", gameId);
        }
        if (platform != null) {
            spec = spec.param("platform", platform);
        }
        if (decoded != null) {
            spec = spec.param("cursorDist", decoded.distanceMeters()).param("cursorId", decoded.id());
        }

        record PinRow(MapPinDto dto, double distanceMeters) {}
        List<PinRow> rows = spec.query((rs, rowNum) -> {
            double distM = rs.getDouble("distance_m");
            double distKm = Math.round((distM / 10.0)) / 100.0;
            // Offset each listing pin slightly around the user's ~500m fuzzed point so multiple games from the same user don't stack identically
            UUID listingId = rs.getObject("listing_id", UUID.class);
            double jitterLat = ((listingId.getMostSignificantBits() & 0xFF) - 128) * 0.000006;
            double jitterLng = ((listingId.getLeastSignificantBits() & 0xFF) - 128) * 0.000006;

            MapPinDto pin = new MapPinDto(
                listingId,
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("locality_name"),
                rs.getBigDecimal("rating_avg"),
                rs.getObject("game_id", UUID.class),
                rs.getString("game_title"),
                rs.getString("game_cover_url"),
                rs.getString("platform"),
                rs.getString("condition"),
                Math.round((rs.getDouble("fuzz_lat") + jitterLat) * 1_000_000.0) / 1_000_000.0,
                Math.round((rs.getDouble("fuzz_lng") + jitterLng) * 1_000_000.0) / 1_000_000.0,
                distKm,
                rs.getInt("match_tier"),
                rs.getTimestamp("created_at").toInstant()
            );
            return new PinRow(pin, distM);
        }).list();

        boolean hasMore = rows.size() > pageSize;
        List<PinRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<MapPinDto> items = pageRows.stream().map(PinRow::dto).toList();
        String nextCursor = hasMore
            ? CursorCodec.encodeDistance(pageRows.getLast().distanceMeters(), pageRows.getLast().dto().listingId())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    private List<MatchCardDto> enrichMatchCards(
        UUID meId,
        List<CandidateRow> candidates,
        String platformFilter
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = candidates.stream().map(CandidateRow::userId).toList();

        // 1. Games they HAVE that I WANT (plus their other HAVE listings)
        record CandidateHaveRow(
            UUID ownerId,
            UUID listingId,
            UUID gameId,
            String title,
            String coverUrl,
            String platform,
            String condition,
            boolean matchesMyWant
        ) {}

        List<CandidateHaveRow> haveRows = jdbcClient.sql("""
                SELECT th.user_id AS owner_id,
                       th.id AS listing_id,
                       th.game_id,
                       g.title,
                       g.cover_url,
                       th.platform,
                       th.condition,
                       EXISTS (
                           SELECT 1 FROM want_listings mw
                           WHERE mw.user_id = :meId
                             AND mw.status = 'ACTIVE'
                             AND mw.game_id = th.game_id
                             AND mw.platform = th.platform
                       ) AS matches_my_want
                FROM have_listings th
                JOIN games g ON g.id = th.game_id
                WHERE th.user_id IN (:candidateIds)
                  AND th.status = 'ACTIVE'
                ORDER BY th.created_at DESC
                """)
            .param("meId", meId)
            .param("candidateIds", candidateIds)
            .query((rs, rowNum) -> new CandidateHaveRow(
                rs.getObject("owner_id", UUID.class),
                rs.getObject("listing_id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                rs.getString("condition"),
                rs.getBoolean("matches_my_want")
            ))
            .list();

        // 2. Games they WANT that I HAVE (returns MY have_listing_id so UI can propose swap in 1 click)
        record CandidateWantRow(
            UUID candidateId,
            UUID myHaveListingId,
            UUID gameId,
            String title,
            String coverUrl,
            String platform,
            String myCondition
        ) {}

        List<CandidateWantRow> wantRows = jdbcClient.sql("""
                SELECT tw.user_id AS candidate_id,
                       mh.id AS my_have_listing_id,
                       tw.game_id,
                       g.title,
                       g.cover_url,
                       tw.platform,
                       mh.condition AS my_condition
                FROM want_listings tw
                JOIN have_listings mh ON mh.game_id = tw.game_id AND mh.platform = tw.platform
                JOIN games g ON g.id = tw.game_id
                WHERE tw.user_id IN (:candidateIds)
                  AND tw.status = 'ACTIVE'
                  AND mh.user_id = :meId
                  AND mh.status = 'ACTIVE'
                ORDER BY tw.created_at DESC
                """)
            .param("meId", meId)
            .param("candidateIds", candidateIds)
            .query((rs, rowNum) -> new CandidateWantRow(
                rs.getObject("candidate_id", UUID.class),
                rs.getObject("my_have_listing_id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                rs.getString("my_condition")
            ))
            .list();

        Map<UUID, List<MatchedGameItemDto>> theyHaveIWantMap = new LinkedHashMap<>();
        Map<UUID, List<MatchedGameItemDto>> otherHaveMap = new LinkedHashMap<>();
        Map<UUID, List<MatchedGameItemDto>> theyWantIHaveMap = new LinkedHashMap<>();

        for (UUID id : candidateIds) {
            theyHaveIWantMap.put(id, new ArrayList<>());
            otherHaveMap.put(id, new ArrayList<>());
            theyWantIHaveMap.put(id, new ArrayList<>());
        }

        for (CandidateHaveRow hr : haveRows) {
            if (platformFilter != null && !platformFilter.equals(hr.platform())) {
                continue;
            }
            MatchedGameItemDto dto = new MatchedGameItemDto(
                hr.listingId(), hr.gameId(), hr.title(), hr.coverUrl(), hr.platform(), hr.condition()
            );
            if (hr.matchesMyWant()) {
                theyHaveIWantMap.get(hr.ownerId()).add(dto);
            } else {
                otherHaveMap.get(hr.ownerId()).add(dto);
            }
        }

        for (CandidateWantRow wr : wantRows) {
            if (platformFilter != null && !platformFilter.equals(wr.platform())) {
                continue;
            }
            theyWantIHaveMap.get(wr.candidateId()).add(new MatchedGameItemDto(
                wr.myHaveListingId(), wr.gameId(), wr.title(), wr.coverUrl(), wr.platform(), wr.myCondition()
            ));
        }

        List<MatchCardDto> result = new ArrayList<>();
        for (CandidateRow c : candidates) {
            UUID uid = c.userId();
            double distKm = Math.round((c.distanceMeters() / 10.0)) / 100.0;
            result.add(new MatchCardDto(
                uid,
                c.displayName(),
                c.avatarSeed(),
                c.localityName(),
                c.consoles(),
                c.ratingAvg(),
                c.ratingCount(),
                c.completedSwapsCount(),
                c.matchTier(),
                c.matchTier() == 2 ? "TWO_WAY" : "ONE_WAY",
                distKm,
                c.distanceMeters(),
                theyHaveIWantMap.getOrDefault(uid, List.of()),
                theyWantIHaveMap.getOrDefault(uid, List.of()),
                otherHaveMap.getOrDefault(uid, List.of())
            ));
        }

        return result;
    }

    private void ensureUserOnboarded(UUID userId) {
        Boolean onboarded = jdbcClient.sql("SELECT onboarding_completed FROM users WHERE id = :id")
            .param("id", userId)
            .query(Boolean.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (!Boolean.TRUE.equals(onboarded)) {
            throw ApiException.badRequest(
                "ONBOARDING_REQUIRED",
                "Complete your profile onboarding (Bangalore locality & consoles) to discover nearby matches"
            );
        }
    }

    private double clampRadiusKm(Double radiusKm) {
        if (radiusKm == null || radiusKm <= 0) {
            return properties.matching().defaultRadiusKm();
        }
        return Math.min(radiusKm, properties.matching().maxRadiusKm());
    }
}