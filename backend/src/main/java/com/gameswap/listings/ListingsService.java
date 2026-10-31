package com.gameswap.listings;

import com.gameswap.common.api.CursorCodec;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.config.GameSwapProperties;
import com.gameswap.common.error.ApiException;
import com.gameswap.common.ratelimit.RateLimiterService;
import com.gameswap.common.ratelimit.RedisStoreService;
import com.gameswap.games.GamesService;
import com.gameswap.listings.ListingDtos.*;
import com.gameswap.users.ConsolePlatform;
import com.gameswap.users.UserDtos.ShelfGameDto;
import com.gameswap.users.UsersService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ListingsService {

    private static final Set<String> VALID_CONDITIONS = Set.of("LIKE_NEW", "GOOD", "WORN");
    private static final Set<String> VALID_SHELF_STATUSES = Set.of("CURRENTLY_PLAYING", "FINISHED");

    private final JdbcClient jdbcClient;
    private final GamesService gamesService;
    private final UsersService usersService;
    private final RateLimiterService rateLimiterService;
    private final RedisStoreService redisStore;
    private final GameSwapProperties properties;

    public ListingsService(
        JdbcClient jdbcClient,
        GamesService gamesService,
        UsersService usersService,
        RateLimiterService rateLimiterService,
        RedisStoreService redisStore,
        GameSwapProperties properties
    ) {
        this.jdbcClient = jdbcClient;
        this.gamesService = gamesService;
        this.usersService = usersService;
        this.rateLimiterService = rateLimiterService;
        this.redisStore = redisStore;
        this.properties = properties;
    }

    public CursorPage<HaveListingDto> listHaveListings(UUID viewerId, UUID targetUserId, String cursor, Integer limit) {
        UUID ownerId = targetUserId != null ? targetUserId : viewerId;
        if (!viewerId.equals(ownerId) && usersService.isBlockedEitherWay(viewerId, ownerId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "User not found");
        }

        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT h.id, h.user_id, h.game_id, g.title, g.cover_url,
                     h.platform, h.condition, h.notes, h.status, h.created_at
              FROM have_listings h
              JOIN games g ON g.id = h.game_id
              WHERE h.user_id = :ownerId AND h.status IN ('ACTIVE', 'LOCKED_IN_SWAP')
              ORDER BY h.created_at DESC, h.id DESC
              LIMIT :limit
              """
            : """
              SELECT h.id, h.user_id, h.game_id, g.title, g.cover_url,
                     h.platform, h.condition, h.notes, h.status, h.created_at
              FROM have_listings h
              JOIN games g ON g.id = h.game_id
              WHERE h.user_id = :ownerId AND h.status IN ('ACTIVE', 'LOCKED_IN_SWAP')
                AND (h.created_at < :cursorTime OR (h.created_at = :cursorTime AND h.id < :cursorId))
              ORDER BY h.created_at DESC, h.id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("ownerId", ownerId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<HaveListingDto> rows = spec.query((rs, rowNum) -> new HaveListingDto(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("game_id", UUID.class),
            rs.getString("title"),
            rs.getString("cover_url"),
            rs.getString("platform"),
            rs.getString("condition"),
            rs.getString("notes"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<HaveListingDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().createdAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public HaveListingDto createHaveListing(UUID userId, CreateHaveListingRequestDto request) {
        gamesService.ensureGameExists(request.gameId());
        String platform = ConsolePlatform.normalizeAndValidate(request.platform());
        String condition = request.condition().trim().toUpperCase();
        if (!VALID_CONDITIONS.contains(condition)) {
            throw ApiException.badRequest("INVALID_CONDITION", "Condition must be LIKE_NEW, GOOD, or WORN");
        }

        UUID listingId = UUID.randomUUID();
        String notes = request.notes() != null ? request.notes().trim() : null;

        jdbcClient.sql("""
                INSERT INTO have_listings (id, user_id, game_id, platform, condition, notes, status)
                VALUES (:id, :userId, :gameId, :platform, :condition, :notes, 'ACTIVE')
                """)
            .param("id", listingId)
            .param("userId", userId)
            .param("gameId", request.gameId())
            .param("platform", platform)
            .param("condition", condition)
            .param("notes", notes)
            .update();

        redisStore.deleteByPrefix("matches:");
        return getHaveListingById(listingId);
    }

    @Transactional
    public void removeHaveListing(UUID userId, UUID listingId) {
        record ListingRow(UUID userId, String status) {}
        ListingRow row = jdbcClient.sql("""
                SELECT user_id, status FROM have_listings WHERE id = :id FOR UPDATE
                """)
            .param("id", listingId)
            .query((rs, rowNum) -> new ListingRow(rs.getObject("user_id", UUID.class), rs.getString("status")))
            .optional()
            .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "HAVE listing not found"));

        if (!row.userId().equals(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "You can only remove your own HAVE listings");
        }
        if ("LOCKED_IN_SWAP".equals(row.status())) {
            throw ApiException.conflict("LISTING_LOCKED", "Cannot remove a game that is locked in an accepted swap");
        }

        jdbcClient.sql("""
                UPDATE have_listings
                SET status = 'REMOVED', updated_at = now()
                WHERE id = :id
                """)
            .param("id", listingId)
            .update();

        redisStore.deleteByPrefix("matches:");
    }

    public CursorPage<WantListingDto> listWantListings(UUID viewerId, UUID targetUserId, String cursor, Integer limit) {
        UUID ownerId = targetUserId != null ? targetUserId : viewerId;
        if (!viewerId.equals(ownerId) && usersService.isBlockedEitherWay(viewerId, ownerId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "User not found");
        }

        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT w.id, w.user_id, w.game_id, g.title, g.cover_url,
                     w.platform, w.status, w.created_at
              FROM want_listings w
              JOIN games g ON g.id = w.game_id
              WHERE w.user_id = :ownerId AND w.status = 'ACTIVE'
              ORDER BY w.created_at DESC, w.id DESC
              LIMIT :limit
              """
            : """
              SELECT w.id, w.user_id, w.game_id, g.title, g.cover_url,
                     w.platform, w.status, w.created_at
              FROM want_listings w
              JOIN games g ON g.id = w.game_id
              WHERE w.user_id = :ownerId AND w.status = 'ACTIVE'
                AND (w.created_at < :cursorTime OR (w.created_at = :cursorTime AND w.id < :cursorId))
              ORDER BY w.created_at DESC, w.id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("ownerId", ownerId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<WantListingDto> rows = spec.query((rs, rowNum) -> new WantListingDto(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("game_id", UUID.class),
            rs.getString("title"),
            rs.getString("cover_url"),
            rs.getString("platform"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<WantListingDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().createdAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public WantListingDto createWantListing(UUID userId, CreateWantListingRequestDto request) {
        rateLimiterService.checkWantMutationRateLimit(userId);
        gamesService.ensureGameExists(request.gameId());
        String platform = ConsolePlatform.normalizeAndValidate(request.platform());

        // Lock user row to prevent race conditions bypassing the max 10 active wants limit
        jdbcClient.sql("SELECT id FROM users WHERE id = :userId FOR UPDATE")
            .param("userId", userId)
            .query(UUID.class)
            .single();

        int maxWants = properties.limits().maxActiveWants();
        Integer activeWants = jdbcClient.sql("""
                SELECT COUNT(*) FROM want_listings
                WHERE user_id = :userId AND status = 'ACTIVE'
                """)
            .param("userId", userId)
            .query(Integer.class)
            .single();

        if (activeWants != null && activeWants >= maxWants) {
            throw ApiException.badRequest(
                "MAX_WANTS_EXCEEDED",
                "You can have at most " + maxWants + " active WANT games at a time. Remove one to add another."
            );
        }

        Optional<UUID> duplicate = jdbcClient.sql("""
                SELECT id FROM want_listings
                WHERE user_id = :userId AND game_id = :gameId AND platform = :platform AND status = 'ACTIVE'
                """)
            .param("userId", userId)
            .param("gameId", request.gameId())
            .param("platform", platform)
            .query(UUID.class)
            .optional();

        if (duplicate.isPresent()) {
            throw ApiException.conflict("WANT_ALREADY_EXISTS", "This game and platform is already on your WANT list");
        }

        UUID wantId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO want_listings (id, user_id, game_id, platform, status)
                VALUES (:id, :userId, :gameId, :platform, 'ACTIVE')
                """)
            .param("id", wantId)
            .param("userId", userId)
            .param("gameId", request.gameId())
            .param("platform", platform)
            .update();

        redisStore.deleteByPrefix("matches:");
        return getWantListingById(wantId);
    }

    @Transactional
    public void removeWantListing(UUID userId, UUID listingId) {
        UUID ownerId = jdbcClient.sql("SELECT user_id FROM want_listings WHERE id = :id")
            .param("id", listingId)
            .query(UUID.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "WANT listing not found"));

        if (!ownerId.equals(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "You can only remove your own WANT listings");
        }

        jdbcClient.sql("UPDATE want_listings SET status = 'REMOVED' WHERE id = :id")
            .param("id", listingId)
            .update();

        redisStore.deleteByPrefix("matches:");
    }

    @Transactional
    public ShelfGameDto setShelfStatus(UUID userId, UUID gameId, ShelfUpdateRequestDto request) {
        gamesService.ensureGameExists(gameId);
        String platform = ConsolePlatform.normalizeAndValidate(request.platform());
        String shelfStatus = request.shelfStatus().trim().toUpperCase();
        if (!VALID_SHELF_STATUSES.contains(shelfStatus)) {
            throw ApiException.badRequest("INVALID_SHELF_STATUS", "Shelf status must be CURRENTLY_PLAYING or FINISHED");
        }

        jdbcClient.sql("""
                INSERT INTO user_game_statuses (id, user_id, game_id, platform, shelf_status, updated_at)
                VALUES (:id, :userId, :gameId, :platform, :shelfStatus, now())
                ON CONFLICT (user_id, game_id) DO UPDATE
                SET platform = EXCLUDED.platform,
                    shelf_status = EXCLUDED.shelf_status,
                    updated_at = now()
                """)
            .param("id", UUID.randomUUID())
            .param("userId", userId)
            .param("gameId", gameId)
            .param("platform", platform)
            .param("shelfStatus", shelfStatus)
            .update();

        return jdbcClient.sql("""
                SELECT s.game_id, g.title, g.cover_url, s.platform, s.shelf_status, s.updated_at
                FROM user_game_statuses s
                JOIN games g ON g.id = s.game_id
                WHERE s.user_id = :userId AND s.game_id = :gameId
                """)
            .param("userId", userId)
            .param("gameId", gameId)
            .query((rs, rowNum) -> new ShelfGameDto(
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                rs.getString("shelf_status"),
                rs.getTimestamp("updated_at").toInstant()
            ))
            .single();
    }

    @Transactional
    public void removeFromShelf(UUID userId, UUID gameId) {
        jdbcClient.sql("DELETE FROM user_game_statuses WHERE user_id = :userId AND game_id = :gameId")
            .param("userId", userId)
            .param("gameId", gameId)
            .update();
    }

    public HaveListingDto getHaveListingById(UUID listingId) {
        return jdbcClient.sql("""
                SELECT h.id, h.user_id, h.game_id, g.title, g.cover_url,
                       h.platform, h.condition, h.notes, h.status, h.created_at
                FROM have_listings h
                JOIN games g ON g.id = h.game_id
                WHERE h.id = :id
                """)
            .param("id", listingId)
            .query((rs, rowNum) -> new HaveListingDto(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                rs.getString("condition"),
                rs.getString("notes"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "HAVE listing not found"));
    }

    public WantListingDto getWantListingById(UUID wantId) {
        return jdbcClient.sql("""
                SELECT w.id, w.user_id, w.game_id, g.title, g.cover_url,
                       w.platform, w.status, w.created_at
                FROM want_listings w
                JOIN games g ON g.id = w.game_id
                WHERE w.id = :id
                """)
            .param("id", wantId)
            .query((rs, rowNum) -> new WantListingDto(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("platform"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "WANT listing not found"));
    }
}

