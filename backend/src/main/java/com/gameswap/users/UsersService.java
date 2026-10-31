package com.gameswap.users;

import com.gameswap.common.api.CursorCodec;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.error.ApiException;
import com.gameswap.common.ratelimit.RedisStoreService;
import com.gameswap.users.UserDtos.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsersService {

    private final JdbcClient jdbcClient;
    private final RedisStoreService redisStore;

    public UsersService(JdbcClient jdbcClient, RedisStoreService redisStore) {
        this.jdbcClient = jdbcClient;
        this.redisStore = redisStore;
    }

    @Transactional
    public MyProfileDto completeOnboarding(UUID userId, OnboardingRequestDto request) {
        String displayName = request.displayName().trim();
        List<String> validConsoles = ConsolePlatform.validateList(request.consoles());
        LocalityDto locality = findLocalityById(request.localityId());

        ensureDisplayNameAvailable(userId, displayName);

        double[] fuzzed = computeDeterministicFuzzedCoords(userId, locality.latitude(), locality.longitude());

        jdbcClient.sql("""
                UPDATE users
                SET display_name = :displayName,
                    consoles = :consoles,
                    locality_id = :localityId,
                    locality_name = :localityName,
                    area_point = ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                    fuzz_lat = :fuzzLat,
                    fuzz_lng = :fuzzLng,
                    bio = :bio,
                    onboarding_completed = true,
                    updated_at = now()
                WHERE id = :userId
                """)
            .param("displayName", displayName)
            .param("consoles", validConsoles.toArray(new String[0]))
            .param("localityId", locality.id())
            .param("localityName", locality.name())
            .param("lng", locality.longitude())
            .param("lat", locality.latitude())
            .param("fuzzLat", fuzzed[0])
            .param("fuzzLng", fuzzed[1])
            .param("bio", request.bio() != null ? request.bio().trim() : null)
            .param("userId", userId)
            .update();

        redisStore.deleteByPrefix("matches:" + userId);
        return getMyProfile(userId);
    }

    @Transactional
    public MyProfileDto updateProfile(UUID userId, UpdateProfileRequestDto request) {
        MyProfileDto current = getMyProfile(userId);

        String displayName = request.displayName() != null ? request.displayName().trim() : current.displayName();
        if (displayName != null) {
            ensureDisplayNameAvailable(userId, displayName);
        }

        List<String> consoles = request.consoles() != null && !request.consoles().isEmpty()
            ? ConsolePlatform.validateList(request.consoles())
            : current.consoles();

        UUID localityId = request.localityId() != null ? request.localityId() : current.localityId();
        String bio = request.bio() != null ? request.bio().trim() : current.bio();

        if (localityId != null && !localityId.equals(current.localityId())) {
            LocalityDto locality = findLocalityById(localityId);
            double[] fuzzed = computeDeterministicFuzzedCoords(userId, locality.latitude(), locality.longitude());
            jdbcClient.sql("""
                    UPDATE users
                    SET display_name = :displayName,
                        consoles = :consoles,
                        locality_id = :localityId,
                        locality_name = :localityName,
                        area_point = ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                        fuzz_lat = :fuzzLat,
                        fuzz_lng = :fuzzLng,
                        bio = :bio,
                        updated_at = now()
                    WHERE id = :userId
                    """)
                .param("displayName", displayName)
                .param("consoles", consoles.toArray(new String[0]))
                .param("localityId", locality.id())
                .param("localityName", locality.name())
                .param("lng", locality.longitude())
                .param("lat", locality.latitude())
                .param("fuzzLat", fuzzed[0])
                .param("fuzzLng", fuzzed[1])
                .param("bio", bio)
                .param("userId", userId)
                .update();
        } else {
            jdbcClient.sql("""
                    UPDATE users
                    SET display_name = :displayName,
                        consoles = :consoles,
                        bio = :bio,
                        updated_at = now()
                    WHERE id = :userId
                    """)
                .param("displayName", displayName)
                .param("consoles", consoles.toArray(new String[0]))
                .param("bio", bio)
                .param("userId", userId)
                .update();
        }

        redisStore.deleteByPrefix("matches:" + userId);
        return getMyProfile(userId);
    }

    public MyProfileDto getMyProfile(UUID userId) {
        return jdbcClient.sql("""
                SELECT u.id, u.display_name, u.avatar_seed, u.bio, u.consoles,
                       u.locality_id, u.locality_name, u.rating_avg, u.rating_count,
                       u.completed_swaps_count, u.onboarding_completed, u.created_at,
                       (SELECT COUNT(*) FROM have_listings h WHERE h.user_id = u.id AND h.status = 'ACTIVE') AS active_have_count,
                       (SELECT COUNT(*) FROM want_listings w WHERE w.user_id = u.id AND w.status = 'ACTIVE') AS active_want_count,
                       (SELECT COUNT(*) FROM notifications n WHERE n.user_id = u.id AND n.is_read = false) AS unread_notifications_count
                FROM users u
                WHERE u.id = :userId
                """)
            .param("userId", userId)
            .query((rs, rowNum) -> new MyProfileDto(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("bio"),
                extractStringArray(rs, "consoles"),
                rs.getObject("locality_id", UUID.class),
                rs.getString("locality_name"),
                rs.getBigDecimal("rating_avg"),
                rs.getInt("rating_count"),
                rs.getInt("completed_swaps_count"),
                rs.getBoolean("onboarding_completed"),
                rs.getInt("active_have_count"),
                rs.getInt("active_want_count"),
                rs.getInt("unread_notifications_count"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    public PublicUserProfileDto getPublicProfile(UUID viewerId, UUID targetUserId) {
        if (!viewerId.equals(targetUserId) && isBlockedEitherWay(viewerId, targetUserId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "Gamer profile not found");
        }

        record BaseProfile(
            UUID id,
            String displayName,
            String avatarSeed,
            String bio,
            List<String> consoles,
            String localityName,
            java.math.BigDecimal ratingAvg,
            int ratingCount,
            int completedSwapsCount,
            java.time.Instant createdAt
        ) {}

        BaseProfile base = jdbcClient.sql("""
                SELECT id, display_name, avatar_seed, bio, consoles, locality_name,
                       rating_avg, rating_count, completed_swaps_count, created_at
                FROM users
                WHERE id = :targetUserId AND onboarding_completed = true AND is_banned = false
                """)
            .param("targetUserId", targetUserId)
            .query((rs, rowNum) -> new BaseProfile(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("bio"),
                extractStringArray(rs, "consoles"),
                rs.getString("locality_name"),
                rs.getBigDecimal("rating_avg"),
                rs.getInt("rating_count"),
                rs.getInt("completed_swaps_count"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Gamer profile not found"));

        List<ShelfGameDto> shelf = jdbcClient.sql("""
                SELECT s.game_id, g.title, g.cover_url, s.platform, s.shelf_status, s.updated_at
                FROM user_game_statuses s
                JOIN games g ON g.id = s.game_id
                WHERE s.user_id = :userId
                ORDER BY s.updated_at DESC
                LIMIT 50
                """)
            .param("userId", targetUserId)
            .query((rs, rowNum) -> new ShelfGameDto(
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                rs.getString("shelf_status"),
                rs.getTimestamp("updated_at").toInstant()
            ))
            .list();

        List<ShelfGameDto> playing = shelf.stream()
            .filter(s -> "CURRENTLY_PLAYING".equals(s.shelfStatus()))
            .toList();
        List<ShelfGameDto> finished = shelf.stream()
            .filter(s -> "FINISHED".equals(s.shelfStatus()))
            .toList();

        List<ProfileListingItemDto> haveListings = jdbcClient.sql("""
                SELECT h.id, h.game_id, g.title, g.cover_url, h.platform, h.condition, h.notes, h.created_at
                FROM have_listings h
                JOIN games g ON g.id = h.game_id
                WHERE h.user_id = :userId AND h.status = 'ACTIVE'
                ORDER BY h.created_at DESC
                LIMIT 50
                """)
            .param("userId", targetUserId)
            .query((rs, rowNum) -> new ProfileListingItemDto(
                rs.getObject("id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                rs.getString("condition"),
                rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();

        List<ProfileListingItemDto> wantListings = jdbcClient.sql("""
                SELECT w.id, w.game_id, g.title, g.cover_url, w.platform, w.created_at
                FROM want_listings w
                JOIN games g ON g.id = w.game_id
                WHERE w.user_id = :userId AND w.status = 'ACTIVE'
                ORDER BY w.created_at DESC
                LIMIT 10
                """)
            .param("userId", targetUserId)
            .query((rs, rowNum) -> new ProfileListingItemDto(
                rs.getObject("id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                null,
                null,
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();

        return new PublicUserProfileDto(
            base.id(),
            base.displayName(),
            base.avatarSeed(),
            base.bio(),
            base.consoles(),
            base.localityName(),
            base.ratingAvg(),
            base.ratingCount(),
            base.completedSwapsCount(),
            playing,
            finished,
            haveListings,
            wantListings,
            base.createdAt()
        );
    }

    public CursorPage<LocalityDto> listLocalities(String query, String cursor, Integer limit) {
        int pageSize = CursorCodec.clampLimit(limit, 25, 50);
        CursorCodec.StringCursor decoded = CursorCodec.decodeString(cursor);
        String searchPattern = (query == null || query.isBlank()) ? "%" : "%" + query.trim() + "%";

        List<LocalityDto> rows;
        if (decoded == null) {
            rows = jdbcClient.sql("""
                    SELECT id, name, pincode, latitude, longitude
                    FROM localities
                    WHERE name ILIKE :q OR pincode ILIKE :q
                    ORDER BY name ASC, id ASC
                    LIMIT :limit
                    """)
                .param("q", searchPattern)
                .param("limit", pageSize + 1)
                .query((rs, rowNum) -> new LocalityDto(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("pincode"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude")
                ))
                .list();
        } else {
            rows = jdbcClient.sql("""
                    SELECT id, name, pincode, latitude, longitude
                    FROM localities
                    WHERE (name ILIKE :q OR pincode ILIKE :q)
                      AND (name > :cursorName OR (name = :cursorName AND id > :cursorId))
                    ORDER BY name ASC, id ASC
                    LIMIT :limit
                    """)
                .param("q", searchPattern)
                .param("cursorName", decoded.value())
                .param("cursorId", decoded.id())
                .param("limit", pageSize + 1)
                .query((rs, rowNum) -> new LocalityDto(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("pincode"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude")
                ))
                .list();
        }

        boolean hasMore = rows.size() > pageSize;
        List<LocalityDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeString(items.getLast().name(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    public CursorPage<MeetupSpotDto> listMeetupSpots(UUID localityId, String cursor, Integer limit) {
        int pageSize = CursorCodec.clampLimit(limit, 25, 50);
        CursorCodec.StringCursor decoded = CursorCodec.decodeString(cursor);

        StringBuilder sql = new StringBuilder("""
            SELECT m.id, m.name, m.category, m.locality_id, l.name AS locality_name,
                   m.address_hint, m.latitude, m.longitude
            FROM meetup_spots m
            JOIN localities l ON l.id = m.locality_id
            WHERE m.is_active = true
            """);

        if (localityId != null) {
            sql.append(" AND m.locality_id = :localityId ");
        }
        if (decoded != null) {
            sql.append(" AND (m.name > :cursorName OR (m.name = :cursorName AND m.id > :cursorId)) ");
        }
        sql.append(" ORDER BY m.name ASC, m.id ASC LIMIT :limit ");

        var spec = jdbcClient.sql(sql.toString()).param("limit", pageSize + 1);
        if (localityId != null) {
            spec = spec.param("localityId", localityId);
        }
        if (decoded != null) {
            spec = spec.param("cursorName", decoded.value()).param("cursorId", decoded.id());
        }

        List<MeetupSpotDto> rows = spec.query((rs, rowNum) -> new MeetupSpotDto(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("category"),
            rs.getObject("locality_id", UUID.class),
            rs.getString("locality_name"),
            rs.getString("address_hint"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude")
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<MeetupSpotDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeString(items.getLast().name(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    public CursorPage<NotificationDto> listNotifications(UUID userId, String cursor, Integer limit) {
        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT id, type, title, body, reference_id, is_read, created_at
              FROM notifications
              WHERE user_id = :userId
              ORDER BY created_at DESC, id DESC
              LIMIT :limit
              """
            : """
              SELECT id, type, title, body, reference_id, is_read, created_at
              FROM notifications
              WHERE user_id = :userId
                AND (created_at < :cursorTime OR (created_at = :cursorTime AND id < :cursorId))
              ORDER BY created_at DESC, id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("userId", userId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<NotificationDto> rows = spec.query((rs, rowNum) -> new NotificationDto(
            rs.getObject("id", UUID.class),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getObject("reference_id", UUID.class),
            rs.getBoolean("is_read"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<NotificationDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().createdAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public void markAllNotificationsRead(UUID userId) {
        jdbcClient.sql("UPDATE notifications SET is_read = true WHERE user_id = :userId AND is_read = false")
            .param("userId", userId)
            .update();
    }

    @Transactional
    public void createNotification(UUID userId, String type, String title, String body, UUID referenceId) {
        jdbcClient.sql("""
                INSERT INTO notifications (user_id, type, title, body, reference_id)
                VALUES (:userId, :type, :title, :body, :referenceId)
                """)
            .param("userId", userId)
            .param("type", type)
            .param("title", title)
            .param("body", body)
            .param("referenceId", referenceId)
            .update();
    }

    public boolean isBlockedEitherWay(UUID userA, UUID userB) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM user_blocks
                WHERE (blocker_id = :userA AND blocked_id = :userB)
                   OR (blocker_id = :userB AND blocked_id = :userA)
                """)
            .param("userA", userA)
            .param("userB", userB)
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }

    public LocalityDto findLocalityById(UUID localityId) {
        return jdbcClient.sql("""
                SELECT id, name, pincode, latitude, longitude
                FROM localities
                WHERE id = :id
                """)
            .param("id", localityId)
            .query((rs, rowNum) -> new LocalityDto(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("pincode"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude")
            ))
            .optional()
            .orElseThrow(() -> ApiException.badRequest("INVALID_LOCALITY", "Selected Bangalore locality does not exist"));
    }

    private void ensureDisplayNameAvailable(UUID userId, String displayName) {
        Optional<UUID> existing = jdbcClient.sql("""
                SELECT id FROM users
                WHERE LOWER(display_name) = LOWER(:displayName) AND id <> :userId
                """)
            .param("displayName", displayName)
            .param("userId", userId)
            .query(UUID.class)
            .optional();
        if (existing.isPresent()) {
            throw ApiException.conflict("DISPLAY_NAME_TAKEN", "Display name '" + displayName + "' is already taken");
        }
    }

    /**
     * Offsets the Bangalore area centroid by ~350m to ~550m in a deterministic direction derived from the user's UUID.
     * Never exposes exact address while ensuring map pins spread naturally across the neighbourhood.
     */
    public static double[] computeDeterministicFuzzedCoords(UUID userId, double centerLat, double centerLng) {
        long msb = userId.getMostSignificantBits();
        long lsb = userId.getLeastSignificantBits();
        double angleRadians = ((msb & 0xFFFFL) / 65535.0) * 2.0 * Math.PI;
        double distanceMeters = 350.0 + ((lsb & 0xFFFFL) / 65535.0) * 200.0; // 350m - 550m (~500m fuzzed area)

        double deltaLat = (distanceMeters * Math.cos(angleRadians)) / 111_320.0;
        double metersPerDegLng = 111_320.0 * Math.cos(Math.toRadians(centerLat));
        double deltaLng = (distanceMeters * Math.sin(angleRadians)) / metersPerDegLng;

        return new double[]{
            Math.round((centerLat + deltaLat) * 1_000_000.0) / 1_000_000.0,
            Math.round((centerLng + deltaLng) * 1_000_000.0) / 1_000_000.0
        };
    }

    public static List<String> extractStringArray(ResultSet rs, String columnName) throws SQLException {
        Array sqlArray = rs.getArray(columnName);
        if (sqlArray == null) {
            return List.of();
        }
        String[] array = (String[]) sqlArray.getArray();
        return array != null ? Arrays.asList(array) : List.of();
    }
}
