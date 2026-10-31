package com.gameswap.games;

import com.gameswap.common.api.CursorCodec;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.error.ApiException;
import com.gameswap.games.GameDtos.*;
import com.gameswap.users.ConsolePlatform;
import com.gameswap.users.UsersService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class GamesService {

    private final JdbcClient jdbcClient;
    private final IgdbClientService igdbClientService;

    public GamesService(JdbcClient jdbcClient, IgdbClientService igdbClientService) {
        this.jdbcClient = jdbcClient;
        this.igdbClientService = igdbClientService;
    }

    public CursorPage<GameDto> searchGames(String query, String platform, String cursor, Integer limit) {
        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        if (query != null && !query.isBlank()) {
            igdbClientService.syncFromIgdbIfConfigured(query);
        }

        CursorCodec.StringCursor decoded = CursorCodec.decodeString(cursor);
        String qPattern = (query == null || query.isBlank()) ? "%" : "%" + query.trim() + "%";
        String normalizedPlatform = (platform != null && !platform.isBlank())
            ? ConsolePlatform.normalizeAndValidate(platform)
            : null;

        StringBuilder sql = new StringBuilder("""
            SELECT g.id, g.igdb_id, g.slug, g.title, g.cover_url, g.summary,
                   g.release_year, g.genres, g.supported_platforms,
                   g.avg_review_rating, g.review_count,
                   (SELECT COUNT(*) FROM have_listings h WHERE h.game_id = g.id AND h.status = 'ACTIVE') AS active_local_copies_count
            FROM games g
            WHERE g.title ILIKE :q
            """);

        if (normalizedPlatform != null) {
            sql.append(" AND :platform = ANY(g.supported_platforms) ");
        }
        if (decoded != null) {
            sql.append(" AND (g.title > :cursorTitle OR (g.title = :cursorTitle AND g.id > :cursorId)) ");
        }
        sql.append(" ORDER BY g.title ASC, g.id ASC LIMIT :limit ");

        var spec = jdbcClient.sql(sql.toString())
            .param("q", qPattern)
            .param("limit", pageSize + 1);
        if (normalizedPlatform != null) {
            spec = spec.param("platform", normalizedPlatform);
        }
        if (decoded != null) {
            spec = spec.param("cursorTitle", decoded.value()).param("cursorId", decoded.id());
        }

        List<GameDto> rows = spec.query(this::mapGameRow).list();
        boolean hasMore = rows.size() > pageSize;
        List<GameDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeString(items.getLast().title(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    public GameDto getGameById(UUID gameId) {
        return jdbcClient.sql("""
                SELECT g.id, g.igdb_id, g.slug, g.title, g.cover_url, g.summary,
                       g.release_year, g.genres, g.supported_platforms,
                       g.avg_review_rating, g.review_count,
                       (SELECT COUNT(*) FROM have_listings h WHERE h.game_id = g.id AND h.status = 'ACTIVE') AS active_local_copies_count
                FROM games g
                WHERE g.id = :gameId
                """)
            .param("gameId", gameId)
            .query(this::mapGameRow)
            .optional()
            .orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "Game not found"));
    }

    public CursorPage<LocalGameOwnerDto> listLocalOwners(
        UUID viewerId,
        UUID gameId,
        String platform,
        String cursor,
        Integer limit
    ) {
        ensureGameExists(gameId);
        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);
        String normalizedPlatform = (platform != null && !platform.isBlank())
            ? ConsolePlatform.normalizeAndValidate(platform)
            : null;

        StringBuilder sql = new StringBuilder("""
            SELECT h.id AS listing_id,
                   u.id AS user_id,
                   u.display_name,
                   u.avatar_seed,
                   u.locality_name,
                   u.rating_avg,
                   u.rating_count,
                   h.platform,
                   h.condition,
                   h.notes,
                   h.created_at,
                   CASE
                     WHEN me.area_point IS NOT NULL AND u.area_point IS NOT NULL
                     THEN ROUND((ST_Distance(u.area_point, me.area_point) / 1000.0)::numeric, 2)::double precision
                     ELSE NULL
                   END AS distance_km
            FROM have_listings h
            JOIN users u ON u.id = h.user_id
            LEFT JOIN users me ON me.id = :viewerId
            WHERE h.game_id = :gameId
              AND h.status = 'ACTIVE'
              AND u.onboarding_completed = true
              AND u.is_banned = false
              AND NOT EXISTS (
                  SELECT 1 FROM user_blocks b
                  WHERE (b.blocker_id = :viewerId AND b.blocked_id = u.id)
                     OR (b.blocker_id = u.id AND b.blocked_id = :viewerId)
              )
            """);

        if (normalizedPlatform != null) {
            sql.append(" AND h.platform = :platform ");
        }
        if (decoded != null) {
            sql.append(" AND (h.created_at < :cursorTime OR (h.created_at = :cursorTime AND h.id < :cursorId)) ");
        }
        sql.append(" ORDER BY h.created_at DESC, h.id DESC LIMIT :limit ");

        var spec = jdbcClient.sql(sql.toString())
            .param("viewerId", viewerId)
            .param("gameId", gameId)
            .param("limit", pageSize + 1);
        if (normalizedPlatform != null) {
            spec = spec.param("platform", normalizedPlatform);
        }
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<LocalGameOwnerDto> rows = spec.query((rs, rowNum) -> {
            double distVal = rs.getDouble("distance_km");
            Double distKm = rs.wasNull() ? null : distVal;
            return new LocalGameOwnerDto(
                rs.getObject("listing_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("locality_name"),
                rs.getBigDecimal("rating_avg"),
                rs.getInt("rating_count"),
                rs.getString("platform"),
                rs.getString("condition"),
                rs.getString("notes"),
                distKm,
                rs.getTimestamp("created_at").toInstant()
            );
        }).list();

        boolean hasMore = rows.size() > pageSize;
        List<LocalGameOwnerDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().listedAt(), items.getLast().listingId())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    public CursorPage<GameReviewDto> listReviews(UUID gameId, String cursor, Integer limit) {
        ensureGameExists(gameId);
        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT r.id, r.game_id, r.user_id, u.display_name, u.avatar_seed, u.locality_name,
                     r.rating, r.comment, r.created_at
              FROM game_reviews r
              JOIN users u ON u.id = r.user_id
              WHERE r.game_id = :gameId
              ORDER BY r.created_at DESC, r.id DESC
              LIMIT :limit
              """
            : """
              SELECT r.id, r.game_id, r.user_id, u.display_name, u.avatar_seed, u.locality_name,
                     r.rating, r.comment, r.created_at
              FROM game_reviews r
              JOIN users u ON u.id = r.user_id
              WHERE r.game_id = :gameId
                AND (r.created_at < :cursorTime OR (r.created_at = :cursorTime AND r.id < :cursorId))
              ORDER BY r.created_at DESC, r.id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("gameId", gameId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<GameReviewDto> rows = spec.query((rs, rowNum) -> new GameReviewDto(
            rs.getObject("id", UUID.class),
            rs.getObject("game_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("avatar_seed"),
            rs.getString("locality_name"),
            rs.getInt("rating"),
            rs.getString("comment"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<GameReviewDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().createdAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public GameReviewDto upsertReview(UUID userId, UUID gameId, CreateGameReviewRequestDto request) {
        ensureGameExists(gameId);
        UUID reviewId = UUID.randomUUID();

        UUID savedId = jdbcClient.sql("""
                INSERT INTO game_reviews (id, game_id, user_id, rating, comment)
                VALUES (:id, :gameId, :userId, :rating, :comment)
                ON CONFLICT (game_id, user_id) DO UPDATE
                SET rating = EXCLUDED.rating,
                    comment = EXCLUDED.comment,
                    created_at = now()
                RETURNING id
                """)
            .param("id", reviewId)
            .param("gameId", gameId)
            .param("userId", userId)
            .param("rating", request.rating())
            .param("comment", request.comment().trim())
            .query(UUID.class)
            .single();

        jdbcClient.sql("""
                UPDATE games
                SET avg_review_rating = COALESCE((SELECT ROUND(AVG(rating)::numeric, 2) FROM game_reviews WHERE game_id = :gameId), 0.00),
                    review_count = (SELECT COUNT(*) FROM game_reviews WHERE game_id = :gameId),
                    updated_at = now()
                WHERE id = :gameId
                """)
            .param("gameId", gameId)
            .update();

        return jdbcClient.sql("""
                SELECT r.id, r.game_id, r.user_id, u.display_name, u.avatar_seed, u.locality_name,
                       r.rating, r.comment, r.created_at
                FROM game_reviews r
                JOIN users u ON u.id = r.user_id
                WHERE r.id = :id
                """)
            .param("id", savedId)
            .query((rs, rowNum) -> new GameReviewDto(
                rs.getObject("id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("locality_name"),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .single();
    }

    public CursorPage<GameDiscussionDto> listDiscussions(UUID gameId, String cursor, Integer limit) {
        ensureGameExists(gameId);
        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT d.id, d.game_id, d.user_id, u.display_name, u.avatar_seed, u.locality_name,
                     d.body, d.created_at
              FROM game_discussions d
              JOIN users u ON u.id = d.user_id
              WHERE d.game_id = :gameId
              ORDER BY d.created_at DESC, d.id DESC
              LIMIT :limit
              """
            : """
              SELECT d.id, d.game_id, d.user_id, u.display_name, u.avatar_seed, u.locality_name,
                     d.body, d.created_at
              FROM game_discussions d
              JOIN users u ON u.id = d.user_id
              WHERE d.game_id = :gameId
                AND (d.created_at < :cursorTime OR (d.created_at = :cursorTime AND d.id < :cursorId))
              ORDER BY d.created_at DESC, d.id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("gameId", gameId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<GameDiscussionDto> rows = spec.query((rs, rowNum) -> new GameDiscussionDto(
            rs.getObject("id", UUID.class),
            rs.getObject("game_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("avatar_seed"),
            rs.getString("locality_name"),
            rs.getString("body"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<GameDiscussionDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().createdAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public GameDiscussionDto createDiscussionPost(UUID userId, UUID gameId, CreateGameDiscussionRequestDto request) {
        ensureGameExists(gameId);
        UUID postId = UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO game_discussions (id, game_id, user_id, body)
                VALUES (:id, :gameId, :userId, :body)
                """)
            .param("id", postId)
            .param("gameId", gameId)
            .param("userId", userId)
            .param("body", request.body().trim())
            .update();

        return jdbcClient.sql("""
                SELECT d.id, d.game_id, d.user_id, u.display_name, u.avatar_seed, u.locality_name,
                       d.body, d.created_at
                FROM game_discussions d
                JOIN users u ON u.id = d.user_id
                WHERE d.id = :id
                """)
            .param("id", postId)
            .query((rs, rowNum) -> new GameDiscussionDto(
                rs.getObject("id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("locality_name"),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .single();
    }

    public void ensureGameExists(UUID gameId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM games WHERE id = :id")
            .param("id", gameId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw ApiException.notFound("GAME_NOT_FOUND", "Game does not exist in catalogue");
        }
    }

    private GameDto mapGameRow(ResultSet rs, int rowNum) throws SQLException {
        int relYear = rs.getInt("release_year");
        Integer releaseYear = rs.wasNull() ? null : relYear;
        return new GameDto(
            rs.getObject("id", UUID.class),
            rs.getLong("igdb_id"),
            rs.getString("slug"),
            rs.getString("title"),
            rs.getString("cover_url"),
            rs.getString("summary"),
            releaseYear,
            UsersService.extractStringArray(rs, "genres"),
            UsersService.extractStringArray(rs, "supported_platforms"),
            rs.getBigDecimal("avg_review_rating"),
            rs.getInt("review_count"),
            rs.getInt("active_local_copies_count")
        );
    }
}

