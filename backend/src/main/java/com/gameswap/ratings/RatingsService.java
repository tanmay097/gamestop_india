package com.gameswap.ratings;

import com.gameswap.common.api.CursorCodec;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.error.ApiException;
import com.gameswap.ratings.RatingDtos.*;
import com.gameswap.users.UsersService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RatingsService {

    private final JdbcClient jdbcClient;
    private final UsersService usersService;

    public RatingsService(JdbcClient jdbcClient, UsersService usersService) {
        this.jdbcClient = jdbcClient;
        this.usersService = usersService;
    }

    @Transactional
    public UserRatingDto submitRating(UUID raterId, UUID swapId, CreateRatingRequestDto request) {
        record SwapParties(UUID proposerId, UUID recipientId, String status) {}
        SwapParties swap = jdbcClient.sql("""
                SELECT proposer_id, recipient_id, status FROM swaps WHERE id = :id FOR UPDATE
                """)
            .param("id", swapId)
            .query((rs, rowNum) -> new SwapParties(
                rs.getObject("proposer_id", UUID.class),
                rs.getObject("recipient_id", UUID.class),
                rs.getString("status")
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("SWAP_NOT_FOUND", "Swap not found"));

        if (!"COMPLETED".equals(swap.status())) {
            throw ApiException.conflict("SWAP_NOT_COMPLETED", "Ratings can only be left after a swap is COMPLETED");
        }
        if (!raterId.equals(swap.proposerId()) && !raterId.equals(swap.recipientId())) {
            throw ApiException.forbidden("FORBIDDEN", "Only participants of this swap can leave a rating");
        }

        Optional<UUID> existing = jdbcClient.sql("""
                SELECT id FROM ratings WHERE swap_id = :swapId AND rater_id = :raterId
                """)
            .param("swapId", swapId)
            .param("raterId", raterId)
            .query(UUID.class)
            .optional();

        if (existing.isPresent()) {
            throw ApiException.conflict("RATING_ALREADY_SUBMITTED", "You have already rated this swap");
        }

        UUID rateeId = raterId.equals(swap.proposerId()) ? swap.recipientId() : swap.proposerId();
        UUID ratingId = UUID.randomUUID();
        String comment = request.comment() != null ? request.comment().trim() : null;

        jdbcClient.sql("""
                INSERT INTO ratings (id, swap_id, rater_id, ratee_id, stars, comment)
                VALUES (:id, :swapId, :raterId, :rateeId, :stars, :comment)
                """)
            .param("id", ratingId)
            .param("swapId", swapId)
            .param("raterId", raterId)
            .param("rateeId", rateeId)
            .param("stars", request.stars())
            .param("comment", comment)
            .update();

        jdbcClient.sql("""
                UPDATE users
                SET rating_avg = COALESCE((SELECT ROUND(AVG(stars)::numeric, 2) FROM ratings WHERE ratee_id = :rateeId), 0.00),
                    rating_count = (SELECT COUNT(*) FROM ratings WHERE ratee_id = :rateeId),
                    updated_at = now()
                WHERE id = :rateeId
                """)
            .param("rateeId", rateeId)
            .update();

        usersService.createNotification(
            rateeId,
            "RATING_RECEIVED",
            "New Swap Rating (" + request.stars() + "★)",
            comment != null && !comment.isEmpty() ? comment : "Your swap partner left you a " + request.stars() + "-star rating!",
            swapId
        );

        return jdbcClient.sql("""
                SELECT r.id, r.swap_id, r.rater_id, u.display_name, u.avatar_seed, u.locality_name,
                       r.ratee_id, r.stars, r.comment, r.created_at
                FROM ratings r
                JOIN users u ON u.id = r.rater_id
                WHERE r.id = :id
                """)
            .param("id", ratingId)
            .query((rs, rowNum) -> new UserRatingDto(
                rs.getObject("id", UUID.class),
                rs.getObject("swap_id", UUID.class),
                rs.getObject("rater_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("locality_name"),
                rs.getObject("ratee_id", UUID.class),
                rs.getInt("stars"),
                rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .single();
    }

    public CursorPage<UserRatingDto> listUserRatings(UUID viewerId, UUID targetUserId, String cursor, Integer limit) {
        if (!viewerId.equals(targetUserId) && usersService.isBlockedEitherWay(viewerId, targetUserId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "User not found");
        }

        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT r.id, r.swap_id, r.rater_id, u.display_name, u.avatar_seed, u.locality_name,
                     r.ratee_id, r.stars, r.comment, r.created_at
              FROM ratings r
              JOIN users u ON u.id = r.rater_id
              WHERE r.ratee_id = :targetUserId
              ORDER BY r.created_at DESC, r.id DESC
              LIMIT :limit
              """
            : """
              SELECT r.id, r.swap_id, r.rater_id, u.display_name, u.avatar_seed, u.locality_name,
                     r.ratee_id, r.stars, r.comment, r.created_at
              FROM ratings r
              JOIN users u ON u.id = r.rater_id
              WHERE r.ratee_id = :targetUserId
                AND (r.created_at < :cursorTime OR (r.created_at = :cursorTime AND r.id < :cursorId))
              ORDER BY r.created_at DESC, r.id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("targetUserId", targetUserId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<UserRatingDto> rows = spec.query((rs, rowNum) -> new UserRatingDto(
            rs.getObject("id", UUID.class),
            rs.getObject("swap_id", UUID.class),
            rs.getObject("rater_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("avatar_seed"),
            rs.getString("locality_name"),
            rs.getObject("ratee_id", UUID.class),
            rs.getInt("stars"),
            rs.getString("comment"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<UserRatingDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().createdAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }
}

