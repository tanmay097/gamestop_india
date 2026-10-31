package com.gameswap.chat;

import com.gameswap.chat.ChatDtos.*;
import com.gameswap.common.api.CursorCodec;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.error.ApiException;
import com.gameswap.common.ratelimit.RateLimiterService;
import com.gameswap.swaps.SwapDtos.SwapDto;
import com.gameswap.swaps.SwapsService;
import com.gameswap.users.UsersService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatService {

    private final JdbcClient jdbcClient;
    private final UsersService usersService;
    private final SwapsService swapsService;
    private final RateLimiterService rateLimiterService;
    private final ChatPubSubRelay chatPubSubRelay;

    public ChatService(
        JdbcClient jdbcClient,
        UsersService usersService,
        SwapsService swapsService,
        RateLimiterService rateLimiterService,
        ChatPubSubRelay chatPubSubRelay
    ) {
        this.jdbcClient = jdbcClient;
        this.usersService = usersService;
        this.swapsService = swapsService;
        this.rateLimiterService = rateLimiterService;
        this.chatPubSubRelay = chatPubSubRelay;
    }

    @Transactional
    public ChatThreadDto getOrCreateThread(UUID userId, UUID recipientId) {
        if (userId.equals(recipientId)) {
            throw ApiException.badRequest("CANNOT_CHAT_WITH_SELF", "You cannot start a chat with yourself");
        }
        if (usersService.isBlockedEitherWay(userId, recipientId)) {
            throw ApiException.forbidden("USER_BLOCKED", "Cannot chat with this user");
        }

        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM users WHERE id = :id AND onboarding_completed = true AND is_banned = false")
            .param("id", recipientId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw ApiException.notFound("USER_NOT_FOUND", "Recipient user not found");
        }

        UUID userA = userId.compareTo(recipientId) < 0 ? userId : recipientId;
        UUID userB = userId.compareTo(recipientId) < 0 ? recipientId : userId;

        jdbcClient.sql("""
                INSERT INTO chat_threads (id, user_a_id, user_b_id, last_message_at, last_message_preview)
                VALUES (:id, :userA, :userB, now(), 'Chat started')
                ON CONFLICT (user_a_id, user_b_id) DO NOTHING
                """)
            .param("id", UUID.randomUUID())
            .param("userA", userA)
            .param("userB", userB)
            .update();

        UUID threadId = jdbcClient.sql("""
                SELECT id FROM chat_threads WHERE user_a_id = :userA AND user_b_id = :userB
                """)
            .param("userA", userA)
            .param("userB", userB)
            .query(UUID.class)
            .single();

        return getThreadById(userId, threadId);
    }

    public ChatThreadDto getThreadById(UUID userId, UUID threadId) {
        return jdbcClient.sql("""
                SELECT t.id,
                       u.id AS other_user_id,
                       u.display_name,
                       u.avatar_seed,
                       u.locality_name,
                       u.rating_avg,
                       t.last_message_preview,
                       t.last_message_at,
                       t.created_at
                FROM chat_threads t
                JOIN users u ON u.id = CASE WHEN t.user_a_id = :userId THEN t.user_b_id ELSE t.user_a_id END
                WHERE t.id = :threadId
                  AND (t.user_a_id = :userId OR t.user_b_id = :userId)
                """)
            .param("userId", userId)
            .param("threadId", threadId)
            .query((rs, rowNum) -> new ChatThreadDto(
                rs.getObject("id", UUID.class),
                rs.getObject("other_user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("avatar_seed"),
                rs.getString("locality_name"),
                rs.getBigDecimal("rating_avg"),
                rs.getString("last_message_preview"),
                rs.getTimestamp("last_message_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("THREAD_NOT_FOUND", "Chat thread not found"));
    }

    public CursorPage<ChatThreadDto> listThreads(UUID userId, String cursor, Integer limit) {
        int pageSize = CursorCodec.clampLimit(limit, 20, 50);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        StringBuilder sql = new StringBuilder("""
            SELECT t.id,
                   u.id AS other_user_id,
                   u.display_name,
                   u.avatar_seed,
                   u.locality_name,
                   u.rating_avg,
                   t.last_message_preview,
                   t.last_message_at,
                   t.created_at
            FROM chat_threads t
            JOIN users u ON u.id = CASE WHEN t.user_a_id = :userId THEN t.user_b_id ELSE t.user_a_id END
            WHERE (t.user_a_id = :userId OR t.user_b_id = :userId)
              AND NOT EXISTS (
                  SELECT 1 FROM user_blocks b
                  WHERE (b.blocker_id = :userId AND b.blocked_id = u.id)
                     OR (b.blocker_id = u.id AND b.blocked_id = :userId)
              )
            """);

        if (decoded != null) {
            sql.append(" AND (t.last_message_at < :cursorTime OR (t.last_message_at = :cursorTime AND t.id < :cursorId)) ");
        }
        sql.append(" ORDER BY t.last_message_at DESC, t.id DESC LIMIT :limit ");

        var spec = jdbcClient.sql(sql.toString())
            .param("userId", userId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        List<ChatThreadDto> rows = spec.query((rs, rowNum) -> new ChatThreadDto(
            rs.getObject("id", UUID.class),
            rs.getObject("other_user_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("avatar_seed"),
            rs.getString("locality_name"),
            rs.getBigDecimal("rating_avg"),
            rs.getString("last_message_preview"),
            rs.getTimestamp("last_message_at").toInstant(),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rows.size() > pageSize;
        List<ChatThreadDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().lastMessageAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    public CursorPage<ChatMessageDto> listMessages(UUID userId, UUID threadId, String cursor, Integer limit) {
        ChatThreadDto thread = getThreadById(userId, threadId);
        if (usersService.isBlockedEitherWay(userId, thread.otherUserId())) {
            throw ApiException.forbidden("USER_BLOCKED", "Cannot view messages with a blocked user");
        }

        int pageSize = CursorCodec.clampLimit(limit, 30, 100);
        CursorCodec.TimeCursor decoded = CursorCodec.decodeTime(cursor);

        String sql = decoded == null
            ? """
              SELECT m.id, m.thread_id, m.sender_id, u.display_name, u.avatar_seed,
                     m.message_type, m.body, m.swap_id, m.meetup_spot_id,
                     ms.name AS meetup_name, ms.address_hint AS meetup_address,
                     m.created_at
              FROM chat_messages m
              JOIN users u ON u.id = m.sender_id
              LEFT JOIN meetup_spots ms ON ms.id = m.meetup_spot_id
              WHERE m.thread_id = :threadId
              ORDER BY m.created_at DESC, m.id DESC
              LIMIT :limit
              """
            : """
              SELECT m.id, m.thread_id, m.sender_id, u.display_name, u.avatar_seed,
                     m.message_type, m.body, m.swap_id, m.meetup_spot_id,
                     ms.name AS meetup_name, ms.address_hint AS meetup_address,
                     m.created_at
              FROM chat_messages m
              JOIN users u ON u.id = m.sender_id
              LEFT JOIN meetup_spots ms ON ms.id = m.meetup_spot_id
              WHERE m.thread_id = :threadId
                AND (m.created_at < :cursorTime OR (m.created_at = :cursorTime AND m.id < :cursorId))
              ORDER BY m.created_at DESC, m.id DESC
              LIMIT :limit
              """;

        var spec = jdbcClient.sql(sql)
            .param("threadId", threadId)
            .param("limit", pageSize + 1);
        if (decoded != null) {
            spec = spec.param("cursorTime", Timestamp.from(decoded.timestamp()))
                       .param("cursorId", decoded.id());
        }

        record RawMsg(
            UUID id,
            UUID threadId,
            UUID senderId,
            String senderDisplayName,
            String senderAvatarSeed,
            String messageType,
            String body,
            UUID swapId,
            UUID meetupSpotId,
            String meetupName,
            String meetupAddress,
            Instant createdAt
        ) {}

        List<RawMsg> rawRows = spec.query((rs, rowNum) -> new RawMsg(
            rs.getObject("id", UUID.class),
            rs.getObject("thread_id", UUID.class),
            rs.getObject("sender_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("avatar_seed"),
            rs.getString("message_type"),
            rs.getString("body"),
            rs.getObject("swap_id", UUID.class),
            rs.getObject("meetup_spot_id", UUID.class),
            rs.getString("meetup_name"),
            rs.getString("meetup_address"),
            rs.getTimestamp("created_at").toInstant()
        )).list();

        boolean hasMore = rawRows.size() > pageSize;
        List<RawMsg> pageRows = hasMore ? rawRows.subList(0, pageSize) : rawRows;

        List<UUID> swapIds = pageRows.stream()
            .map(RawMsg::swapId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<UUID, SwapDto> swapMap = swapsService.getSwapsByIds(userId, swapIds);

        List<ChatMessageDto> items = pageRows.stream()
            .map(r -> new ChatMessageDto(
                r.id(),
                r.threadId(),
                r.senderId(),
                r.senderDisplayName(),
                r.senderAvatarSeed(),
                r.messageType(),
                r.body(),
                r.swapId(),
                r.swapId() != null ? swapMap.get(r.swapId()) : null,
                r.meetupSpotId(),
                r.meetupName(),
                r.meetupAddress(),
                r.createdAt()
            ))
            .toList();

        String nextCursor = hasMore
            ? CursorCodec.encodeTime(items.getLast().createdAt(), items.getLast().id())
            : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    @Transactional
    public ChatMessageDto sendMessage(UUID userId, UUID threadId, SendMessageRequestDto request) {
        rateLimiterService.checkChatMessageRateLimit(userId);
        ChatThreadDto thread = getThreadById(userId, threadId);
        if (usersService.isBlockedEitherWay(userId, thread.otherUserId())) {
            throw ApiException.forbidden("USER_BLOCKED", "Cannot send messages to a blocked user");
        }

        String meetupName = null;
        String meetupAddress = null;
        if (request.meetupSpotId() != null) {
            record SpotInfo(String name, String addressHint) {}
            SpotInfo spot = jdbcClient.sql("SELECT name, address_hint FROM meetup_spots WHERE id = :id AND is_active = true")
                .param("id", request.meetupSpotId())
                .query((rs, rowNum) -> new SpotInfo(rs.getString("name"), rs.getString("address_hint")))
                .optional()
                .orElseThrow(() -> ApiException.badRequest("INVALID_MEETUP_SPOT", "Meetup spot does not exist"));
            meetupName = spot.name();
            meetupAddress = spot.addressHint();
        }

        String messageType = request.meetupSpotId() != null ? "MEETUP_SUGGESTION" : "TEXT";
        UUID msgId = UUID.randomUUID();
        Instant now = Instant.now();
        String body = request.body().trim();

        jdbcClient.sql("""
                INSERT INTO chat_messages (id, thread_id, sender_id, message_type, body, meetup_spot_id, created_at)
                VALUES (:id, :threadId, :senderId, :messageType, :body, :meetupSpotId, :createdAt)
                """)
            .param("id", msgId)
            .param("threadId", threadId)
            .param("senderId", userId)
            .param("messageType", messageType)
            .param("body", body)
            .param("meetupSpotId", request.meetupSpotId())
            .param("createdAt", Timestamp.from(now))
            .update();

        String preview = body.length() > 155 ? body.substring(0, 155) + "..." : body;
        jdbcClient.sql("""
                UPDATE chat_threads
                SET last_message_at = :now,
                    last_message_preview = :preview
                WHERE id = :threadId
                """)
            .param("now", Timestamp.from(now))
            .param("preview", preview)
            .param("threadId", threadId)
            .update();

        record SenderInfo(String displayName, String avatarSeed) {}
        SenderInfo sender = jdbcClient.sql("SELECT display_name, avatar_seed FROM users WHERE id = :id")
            .param("id", userId)
            .query((rs, rowNum) -> new SenderInfo(rs.getString("display_name"), rs.getString("avatar_seed")))
            .single();

        ChatMessageDto msgDto = new ChatMessageDto(
            msgId,
            threadId,
            userId,
            sender.displayName(),
            sender.avatarSeed(),
            messageType,
            body,
            null,
            null,
            request.meetupSpotId(),
            meetupName,
            meetupAddress,
            now
        );

        chatPubSubRelay.publishChatMessage(threadId, msgDto);
        return msgDto;
    }
}

