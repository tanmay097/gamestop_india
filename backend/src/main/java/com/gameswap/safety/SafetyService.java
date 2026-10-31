package com.gameswap.safety;

import com.gameswap.common.api.CursorCodec;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.error.ApiException;
import com.gameswap.common.ratelimit.RedisStoreService;
import com.gameswap.safety.SafetyDtos.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SafetyService {

    private final JdbcClient jdbcClient;
    private final RedisStoreService redisStore;

    public SafetyService(JdbcClient jdbcClient, RedisStoreService redisStore) {
        this.jdbcClient = jdbcClient;
        this.redisStore = redisStore;
    }

    @Transactional
    public void blockUser(UUID blockerId, UUID targetUserId) {
        if (blockerId.equals(targetUserId)) {
            throw ApiException.badRequest("CANNOT_BLOCK_SELF", "You cannot block yourself");
        }

        ensureUserExists(targetUserId);

        jdbcClient.sql("""
                INSERT INTO user_blocks (blocker_id, blocked_id)
                VALUES (:blockerId, :blockedId)
                ON CONFLICT (blocker_id, blocked_id) DO NOTHING
                """)
            .param("blockerId", blockerId)
            .param("blockedId", targetUserId)
            .update();

        // Cancel any proposed swaps between the two users
        jdbcClient.sql("""
                UPDATE swaps
                SET status = 'CANCELLED', updated_at = now()
                WHERE status = 'PROPOSED'
                  AND ((proposer_id = :blockerId AND recipient_id = :blockedId)
                    OR (proposer_id = :blockedId AND recipient_id = :blockerId))
                """)
            .param("blockerId", blockerId)
            .param("blockedId", targetUserId)
            .update();

        redisStore.deleteByPrefix("matches:" + blockerId);
        redisStore.deleteByPrefix("matches:" + targetUserId);
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID targetUserId) {
        jdbcClient.sql("""
                DELETE FROM user_blocks
                WHERE blocker_id = :blockerId AND blocked_id = :blockedId
                """)
            .param("blockerId", blockerId)
            .param("blockedId", targetUserId)
            .update();

        redisStore.deleteByPrefix("matches:" + blockerId);
        redisStore.deleteByPrefix("matches:" + targetUserId);
    }

    public CursorPage<BlockedUserDto> listBlockedUsers(UUID blockerId, String cursor, Integer limit) {
        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT b.id, b.blocked_id, u.display_name, u.avatar_seed, u.locality_name, b.created_at
              FROM user_blocks b
              JOIN users u ON u.id = b.blocked_id
              WHERE b.blocker_id = :blockerId
              ORDER BY b.created_at DESC, b.id DESC
              LIMIT :limit
              """
            : """
              SELECT b.id, b.blocked_id, u.display_name, u.avatar_seed, u.locality_name, b.created_at
              FROM user_blocks b
              JOIN users u ON u.id = b.blocked_id
              WHERE b.blocker_id = :blockerId
                AND (b.created_at < :cursorTime OR (b.created_at = :cursorTime AND b.id < :cursorId))
              ORDER BY b.created_at DESC, b.id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("blockerId", blockerId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<BlockedUserDto> rows = spec.query((rs, rowNum) -> new BlockedUserDto(
            rs.getObject("id", UUID.class),
            rs.getObject("blocked_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("avatar_seed"),
            rs.getString("locality_name"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<BlockedUserDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().blockedAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public UserReportDto reportUser(UUID reporterId, ReportUserRequestDto request) {
        if (reporterId.equals(request.reportedUserId())) {
            throw ApiException.badRequest("CANNOT_REPORT_SELF", "You cannot report yourself");
        }
        ensureUserExists(request.reportedUserId());

        UUID reportId = UUID.randomUUID();
        Instant now = Instant.now();
        String reason = request.reason().trim().toUpperCase();
        String details = request.details() != null ? request.details().trim() : null;

        jdbcClient.sql("""
                INSERT INTO user_reports (id, reporter_id, reported_id, reason, details, status, created_at)
                VALUES (:id, :reporterId, :reportedId, :reason, :details, 'OPEN', :createdAt)
                """)
            .param("id", reportId)
            .param("reporterId", reporterId)
            .param("reportedId", request.reportedUserId())
            .param("reason", reason)
            .param("details", details)
            .param("createdAt", Timestamp.from(now))
            .update();

        return new UserReportDto(
            reportId,
            reporterId,
            request.reportedUserId(),
            reason,
            details,
            "OPEN",
            now
        );
    }

    private void ensureUserExists(UUID userId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM users WHERE id = :id")
            .param("id", userId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw ApiException.notFound("USER_NOT_FOUND", "Target user does not exist");
        }
    }
}
