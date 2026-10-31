package com.gameswap.swaps;

import com.gameswap.chat.ChatDtos.ChatMessageDto;
import com.gameswap.chat.ChatPubSubRelay;
import com.gameswap.common.error.ApiException;
import com.gameswap.common.ratelimit.RedisStoreService;
import com.gameswap.swaps.SwapDtos.*;
import com.gameswap.users.UsersService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SwapsService {

    private final JdbcClient jdbcClient;
    private final UsersService usersService;
    private final ChatPubSubRelay chatPubSubRelay;
    private final RedisStoreService redisStore;

    public SwapsService(
        JdbcClient jdbcClient,
        UsersService usersService,
        ChatPubSubRelay chatPubSubRelay,
        RedisStoreService redisStore
    ) {
        this.jdbcClient = jdbcClient;
        this.usersService = usersService;
        this.chatPubSubRelay = chatPubSubRelay;
        this.redisStore = redisStore;
    }

    @Transactional
    public SwapDto proposeSwap(UUID proposerId, ProposeSwapRequestDto request) {
        record ThreadParticipants(UUID userAId, UUID userBId) {}
        ThreadParticipants thread = jdbcClient.sql("""
                SELECT user_a_id, user_b_id FROM chat_threads WHERE id = :threadId
                """)
            .param("threadId", request.threadId())
            .query((rs, rowNum) -> new ThreadParticipants(
                rs.getObject("user_a_id", UUID.class),
                rs.getObject("user_b_id", UUID.class)
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("THREAD_NOT_FOUND", "Chat thread not found"));

        if (!proposerId.equals(thread.userAId()) && !proposerId.equals(thread.userBId())) {
            throw ApiException.forbidden("FORBIDDEN", "You are not a participant in this chat thread");
        }

        UUID recipientId = proposerId.equals(thread.userAId()) ? thread.userBId() : thread.userAId();
        if (usersService.isBlockedEitherWay(proposerId, recipientId)) {
            throw ApiException.forbidden("USER_BLOCKED", "Cannot propose swap with a blocked user");
        }

        ListingForSwap myListing = fetchListingForSwap(request.myHaveListingId());
        ListingForSwap theirListing = fetchListingForSwap(request.theirHaveListingId());

        if (!myListing.userId().equals(proposerId)) {
            throw ApiException.badRequest("INVALID_PROPOSER_LISTING", "myHaveListingId must belong to you");
        }
        if (!theirListing.userId().equals(recipientId)) {
            throw ApiException.badRequest("INVALID_RECIPIENT_LISTING", "theirHaveListingId must belong to the other user");
        }
        if (!"ACTIVE".equals(myListing.status())) {
            throw ApiException.conflict("LISTING_NOT_ACTIVE", "Your game listing is no longer active");
        }
        if (!"ACTIVE".equals(theirListing.status())) {
            throw ApiException.conflict("LISTING_NOT_ACTIVE", "Their game listing is no longer active");
        }

        if (request.meetupSpotId() != null) {
            Integer spotCount = jdbcClient.sql("SELECT COUNT(*) FROM meetup_spots WHERE id = :id AND is_active = true")
                .param("id", request.meetupSpotId())
                .query(Integer.class)
                .single();
            if (spotCount == null || spotCount == 0) {
                throw ApiException.badRequest("INVALID_MEETUP_SPOT", "Selected meetup spot does not exist");
            }
        }

        UUID swapId = UUID.randomUUID();
        Timestamp meetupTs = request.meetupTime() != null ? Timestamp.from(request.meetupTime()) : null;

        jdbcClient.sql("""
                INSERT INTO swaps (
                    id, thread_id, proposer_id, recipient_id,
                    proposer_have_listing_id, recipient_have_listing_id,
                    meetup_spot_id, meetup_time, status
                ) VALUES (
                    :id, :threadId, :proposerId, :recipientId,
                    :myListingId, :theirListingId,
                    :meetupSpotId, :meetupTime, 'PROPOSED'
                )
                """)
            .param("id", swapId)
            .param("threadId", request.threadId())
            .param("proposerId", proposerId)
            .param("recipientId", recipientId)
            .param("myListingId", myListing.id())
            .param("theirListingId", theirListing.id())
            .param("meetupSpotId", request.meetupSpotId())
            .param("meetupTime", meetupTs)
            .update();

        String bodyText = (request.message() != null && !request.message().isBlank())
            ? request.message().trim()
            : String.format("Proposed swap: %s (%s) for %s (%s)",
                myListing.title(), myListing.platform(), theirListing.title(), theirListing.platform());

        SwapDto swapDto = getSwapById(proposerId, swapId);
        emitSwapChatMessage(request.threadId(), proposerId, "SWAP_PROPOSAL", bodyText, swapId, request.meetupSpotId(), swapDto);

        usersService.createNotification(
            recipientId,
            "SWAP_PROPOSED",
            "New Swap Proposal!",
            swapDto.proposerDisplayName() + " offered " + myListing.title() + " for your " + theirListing.title(),
            swapId
        );

        return swapDto;
    }

    @Transactional
    public SwapDto acceptSwap(UUID userId, UUID swapId) {
        LockedSwap swap = lockSwap(swapId);
        if (!swap.recipientId().equals(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "Only the recipient of a swap proposal can accept it");
        }
        if (!"PROPOSED".equals(swap.status())) {
            throw ApiException.conflict("INVALID_SWAP_STATE", "Only PROPOSED swaps can be accepted (current: " + swap.status() + ")");
        }
        if (usersService.isBlockedEitherWay(swap.proposerId(), swap.recipientId())) {
            throw ApiException.forbidden("USER_BLOCKED", "Cannot accept swap with a blocked user");
        }

        // Lock both HAVE listings in canonical UUID order to prevent deadlocks
        lockHaveListingsInOrder(swap.proposerHaveListingId(), swap.recipientHaveListingId());

        String proposerStatus = jdbcClient.sql("SELECT status FROM have_listings WHERE id = :id")
            .param("id", swap.proposerHaveListingId())
            .query(String.class)
            .single();
        String recipientStatus = jdbcClient.sql("SELECT status FROM have_listings WHERE id = :id")
            .param("id", swap.recipientHaveListingId())
            .query(String.class)
            .single();

        if (!"ACTIVE".equals(proposerStatus) || !"ACTIVE".equals(recipientStatus)) {
            throw ApiException.conflict("LISTING_UNAVAILABLE", "One of the games in this swap is no longer available");
        }

        jdbcClient.sql("""
                UPDATE have_listings
                SET status = 'LOCKED_IN_SWAP', updated_at = now()
                WHERE id IN (:a, :b)
                """)
            .param("a", swap.proposerHaveListingId())
            .param("b", swap.recipientHaveListingId())
            .update();

        jdbcClient.sql("""
                UPDATE swaps
                SET status = 'ACCEPTED', updated_at = now()
                WHERE id = :swapId
                """)
            .param("swapId", swapId)
            .update();

        // Automatically decline other pending PROPOSED swaps involving either physical copy
        jdbcClient.sql("""
                UPDATE swaps
                SET status = 'DECLINED', updated_at = now()
                WHERE id <> :swapId
                  AND status = 'PROPOSED'
                  AND (proposer_have_listing_id IN (:a, :b) OR recipient_have_listing_id IN (:a, :b))
                """)
            .param("swapId", swapId)
            .param("a", swap.proposerHaveListingId())
            .param("b", swap.recipientHaveListingId())
            .update();

        redisStore.deleteByPrefix("matches:");

        SwapDto updated = getSwapById(userId, swapId);
        emitSwapChatMessage(
            swap.threadId(),
            userId,
            "SYSTEM",
            updated.recipientDisplayName() + " accepted the swap! Meet up in person and both confirm handoff to complete.",
            swapId,
            swap.meetupSpotId(),
            updated
        );

        usersService.createNotification(
            swap.proposerId(),
            "SWAP_ACCEPTED",
            "Swap Accepted!",
            updated.recipientDisplayName() + " accepted your swap proposal.",
            swapId
        );

        return updated;
    }

    @Transactional
    public SwapDto declineSwap(UUID userId, UUID swapId) {
        LockedSwap swap = lockSwap(swapId);
        if (!swap.recipientId().equals(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "Only the recipient can decline a swap proposal");
        }
        if (!"PROPOSED".equals(swap.status())) {
            throw ApiException.conflict("INVALID_SWAP_STATE", "Only PROPOSED swaps can be declined");
        }

        jdbcClient.sql("UPDATE swaps SET status = 'DECLINED', updated_at = now() WHERE id = :id")
            .param("id", swapId)
            .update();

        SwapDto updated = getSwapById(userId, swapId);
        emitSwapChatMessage(
            swap.threadId(),
            userId,
            "SYSTEM",
            updated.recipientDisplayName() + " declined the swap proposal.",
            swapId,
            null,
            updated
        );

        usersService.createNotification(
            swap.proposerId(),
            "SWAP_DECLINED",
            "Swap Declined",
            updated.recipientDisplayName() + " declined your swap proposal.",
            swapId
        );

        return updated;
    }

    @Transactional
    public SwapDto cancelSwap(UUID userId, UUID swapId) {
        LockedSwap swap = lockSwap(swapId);
        if (!swap.proposerId().equals(userId) && !swap.recipientId().equals(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "Only swap participants can cancel this swap");
        }
        if (!"PROPOSED".equals(swap.status()) && !"ACCEPTED".equals(swap.status())) {
            throw ApiException.conflict("INVALID_SWAP_STATE", "Cannot cancel swap in status " + swap.status());
        }

        if ("ACCEPTED".equals(swap.status())) {
            lockHaveListingsInOrder(swap.proposerHaveListingId(), swap.recipientHaveListingId());
            jdbcClient.sql("""
                    UPDATE have_listings
                    SET status = 'ACTIVE', updated_at = now()
                    WHERE id IN (:a, :b) AND status = 'LOCKED_IN_SWAP'
                    """)
                .param("a", swap.proposerHaveListingId())
                .param("b", swap.recipientHaveListingId())
                .update();
        }

        jdbcClient.sql("UPDATE swaps SET status = 'CANCELLED', updated_at = now() WHERE id = :id")
            .param("id", swapId)
            .update();

        redisStore.deleteByPrefix("matches:");

        SwapDto updated = getSwapById(userId, swapId);
        UUID otherParty = swap.proposerId().equals(userId) ? swap.recipientId() : swap.proposerId();
        emitSwapChatMessage(
            swap.threadId(),
            userId,
            "SYSTEM",
            "Swap was cancelled.",
            swapId,
            null,
            updated
        );

        usersService.createNotification(
            otherParty,
            "SWAP_CANCELLED",
            "Swap Cancelled",
            "A swap in your chat thread was cancelled.",
            swapId
        );

        return updated;
    }

    @Transactional
    public SwapDto confirmSwapCompletion(UUID userId, UUID swapId) {
        LockedSwap swap = lockSwap(swapId);
        if (!swap.proposerId().equals(userId) && !swap.recipientId().equals(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "Only swap participants can confirm swap completion");
        }
        if (!"ACCEPTED".equals(swap.status())) {
            throw ApiException.conflict("INVALID_SWAP_STATE", "Swap must be ACCEPTED before confirming completion (current: " + swap.status() + ")");
        }

        boolean proposerConfirmed = swap.proposerConfirmed() || swap.proposerId().equals(userId);
        boolean recipientConfirmed = swap.recipientConfirmed() || swap.recipientId().equals(userId);

        if (proposerConfirmed && recipientConfirmed) {
            // Both confirmed! Complete the swap and atomically move the games between HAVE lists
            lockHaveListingsInOrder(swap.proposerHaveListingId(), swap.recipientHaveListingId());

            record FullListing(UUID id, UUID userId, UUID gameId, String platform, String condition, String notes) {}
            FullListing proposerCopy = jdbcClient.sql("""
                    SELECT id, user_id, game_id, platform, condition, notes
                    FROM have_listings WHERE id = :id
                    """)
                .param("id", swap.proposerHaveListingId())
                .query((rs, rowNum) -> new FullListing(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("game_id", UUID.class),
                    rs.getString("platform"),
                    rs.getString("condition"),
                    rs.getString("notes")
                ))
                .single();

            FullListing recipientCopy = jdbcClient.sql("""
                    SELECT id, user_id, game_id, platform, condition, notes
                    FROM have_listings WHERE id = :id
                    """)
                .param("id", swap.recipientHaveListingId())
                .query((rs, rowNum) -> new FullListing(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("game_id", UUID.class),
                    rs.getString("platform"),
                    rs.getString("condition"),
                    rs.getString("notes")
                ))
                .single();

            // 1. Mark original physical listings as SWAPPED
            jdbcClient.sql("""
                    UPDATE have_listings
                    SET status = 'SWAPPED', updated_at = now()
                    WHERE id IN (:a, :b)
                    """)
                .param("a", proposerCopy.id())
                .param("b", recipientCopy.id())
                .update();

            // 2. Insert new ACTIVE have_listing for Proposer with Recipient's game
            jdbcClient.sql("""
                    INSERT INTO have_listings (id, user_id, game_id, platform, condition, notes, status)
                    VALUES (:id, :userId, :gameId, :platform, :condition, :notes, 'ACTIVE')
                    """)
                .param("id", UUID.randomUUID())
                .param("userId", swap.proposerId())
                .param("gameId", recipientCopy.gameId())
                .param("platform", recipientCopy.platform())
                .param("condition", recipientCopy.condition())
                .param("notes", recipientCopy.notes())
                .update();

            // 3. Insert new ACTIVE have_listing for Recipient with Proposer's game
            jdbcClient.sql("""
                    INSERT INTO have_listings (id, user_id, game_id, platform, condition, notes, status)
                    VALUES (:id, :userId, :gameId, :platform, :condition, :notes, 'ACTIVE')
                    """)
                .param("id", UUID.randomUUID())
                .param("userId", swap.recipientId())
                .param("gameId", proposerCopy.gameId())
                .param("platform", proposerCopy.platform())
                .param("condition", proposerCopy.condition())
                .param("notes", proposerCopy.notes())
                .update();

            // 4. Fulfill corresponding active WANT listings for both users
            jdbcClient.sql("""
                    UPDATE want_listings
                    SET status = 'FULFILLED'
                    WHERE user_id = :userId AND game_id = :gameId AND platform = :platform AND status = 'ACTIVE'
                    """)
                .param("userId", swap.proposerId())
                .param("gameId", recipientCopy.gameId())
                .param("platform", recipientCopy.platform())
                .update();

            jdbcClient.sql("""
                    UPDATE want_listings
                    SET status = 'FULFILLED'
                    WHERE user_id = :userId AND game_id = :gameId AND platform = :platform AND status = 'ACTIVE'
                    """)
                .param("userId", swap.recipientId())
                .param("gameId", proposerCopy.gameId())
                .param("platform", proposerCopy.platform())
                .update();

            // 5. Update swap state to COMPLETED
            jdbcClient.sql("""
                    UPDATE swaps
                    SET status = 'COMPLETED',
                        proposer_confirmed = true,
                        recipient_confirmed = true,
                        completed_at = now(),
                        updated_at = now()
                    WHERE id = :swapId
                    """)
                .param("swapId", swapId)
                .update();

            // 6. Increment completed_swaps_count on both users
            jdbcClient.sql("""
                    UPDATE users
                    SET completed_swaps_count = completed_swaps_count + 1,
                        updated_at = now()
                    WHERE id IN (:u1, :u2)
                    """)
                .param("u1", swap.proposerId())
                .param("u2", swap.recipientId())
                .update();

            redisStore.deleteByPrefix("matches:");

            SwapDto completedDto = getSwapById(userId, swapId);
            emitSwapChatMessage(
                swap.threadId(),
                userId,
                "SYSTEM",
                "Swap completed! Games have been automatically transferred between your HAVE lists. Leave a rating!",
                swapId,
                null,
                completedDto
            );

            usersService.createNotification(
                swap.proposerId(),
                "SWAP_COMPLETED",
                "Swap Completed!",
                "Your swap is complete and your HAVE list has been updated. Rate your swap partner!",
                swapId
            );
            usersService.createNotification(
                swap.recipientId(),
                "SWAP_COMPLETED",
                "Swap Completed!",
                "Your swap is complete and your HAVE list has been updated. Rate your swap partner!",
                swapId
            );

            return completedDto;
        } else {
            jdbcClient.sql("""
                    UPDATE swaps
                    SET proposer_confirmed = :proposerConfirmed,
                        recipient_confirmed = :recipientConfirmed,
                        updated_at = now()
                    WHERE id = :swapId
                    """)
                .param("proposerConfirmed", proposerConfirmed)
                .param("recipientConfirmed", recipientConfirmed)
                .param("swapId", swapId)
                .update();

            SwapDto partialDto = getSwapById(userId, swapId);
            UUID otherUserId = swap.proposerId().equals(userId) ? swap.recipientId() : swap.proposerId();
            emitSwapChatMessage(
                swap.threadId(),
                userId,
                "SYSTEM",
                "One participant confirmed the in-person handoff. Waiting for the other participant to confirm.",
                swapId,
                null,
                partialDto
            );

            usersService.createNotification(
                otherUserId,
                "SWAP_CONFIRM_PENDING",
                "Confirm Swap Handoff",
                "Your swap partner confirmed the handoff. Confirm on your end to complete the swap!",
                swapId
            );

            return partialDto;
        }
    }

    public SwapDto getSwapById(UUID viewerId, UUID swapId) {
        Map<UUID, SwapDto> map = getSwapsByIds(viewerId, List.of(swapId));
        SwapDto dto = map.get(swapId);
        if (dto == null) {
            throw ApiException.notFound("SWAP_NOT_FOUND", "Swap not found");
        }
        return dto;
    }

    public Map<UUID, SwapDto> getSwapsByIds(UUID viewerId, List<UUID> swapIds) {
        if (swapIds == null || swapIds.isEmpty()) {
            return Map.of();
        }

        List<SwapDto> list = jdbcClient.sql("""
                SELECT s.id, s.thread_id, s.proposer_id, pu.display_name AS proposer_name,
                       s.recipient_id, ru.display_name AS recipient_name,
                       s.proposer_have_listing_id, ph.game_id AS p_game_id, pg.title AS p_game_title,
                       pg.cover_url AS p_game_cover, ph.platform AS p_platform, ph.condition AS p_condition,
                       s.recipient_have_listing_id, rh.game_id AS r_game_id, rg.title AS r_game_title,
                       rg.cover_url AS r_game_cover, rh.platform AS r_platform, rh.condition AS r_condition,
                       s.meetup_spot_id, m.name AS meetup_name, m.address_hint AS meetup_address,
                       s.meetup_time, s.status, s.proposer_confirmed, s.recipient_confirmed,
                       s.completed_at, s.created_at, s.updated_at,
                       EXISTS (
                           SELECT 1 FROM ratings rt
                           WHERE rt.swap_id = s.id AND rt.rater_id = :viewerId
                       ) AS my_rating_submitted
                FROM swaps s
                JOIN users pu ON pu.id = s.proposer_id
                JOIN users ru ON ru.id = s.recipient_id
                JOIN have_listings ph ON ph.id = s.proposer_have_listing_id
                JOIN games pg ON pg.id = ph.game_id
                JOIN have_listings rh ON rh.id = s.recipient_have_listing_id
                JOIN games rg ON rg.id = rh.game_id
                LEFT JOIN meetup_spots m ON m.id = s.meetup_spot_id
                WHERE s.id IN (:swapIds)
                """)
            .param("viewerId", viewerId)
            .param("swapIds", swapIds)
            .query((rs, rowNum) -> {
                Timestamp meetupTs = rs.getTimestamp("meetup_time");
                Timestamp completedTs = rs.getTimestamp("completed_at");
                return new SwapDto(
                    rs.getObject("id", UUID.class),
                    rs.getObject("thread_id", UUID.class),
                    rs.getObject("proposer_id", UUID.class),
                    rs.getString("proposer_name"),
                    rs.getObject("recipient_id", UUID.class),
                    rs.getString("recipient_name"),
                    new SwapListingSummaryDto(
                        rs.getObject("proposer_have_listing_id", UUID.class),
                        rs.getObject("proposer_id", UUID.class),
                        rs.getString("proposer_name"),
                        rs.getObject("p_game_id", UUID.class),
                        rs.getString("p_game_title"),
                        rs.getString("p_game_cover"),
                        rs.getString("p_platform"),
                        rs.getString("p_condition")
                    ),
                    new SwapListingSummaryDto(
                        rs.getObject("recipient_have_listing_id", UUID.class),
                        rs.getObject("recipient_id", UUID.class),
                        rs.getString("recipient_name"),
                        rs.getObject("r_game_id", UUID.class),
                        rs.getString("r_game_title"),
                        rs.getString("r_game_cover"),
                        rs.getString("r_platform"),
                        rs.getString("r_condition")
                    ),
                    rs.getObject("meetup_spot_id", UUID.class),
                    rs.getString("meetup_name"),
                    rs.getString("meetup_address"),
                    meetupTs != null ? meetupTs.toInstant() : null,
                    rs.getString("status"),
                    rs.getBoolean("proposer_confirmed"),
                    rs.getBoolean("recipient_confirmed"),
                    rs.getBoolean("my_rating_submitted"),
                    completedTs != null ? completedTs.toInstant() : null,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            })
            .list();

        Map<UUID, SwapDto> result = new LinkedHashMap<>();
        for (SwapDto s : list) {
            result.put(s.id(), s);
        }
        return result;
    }

    private record LockedSwap(
        UUID id,
        UUID threadId,
        UUID proposerId,
        UUID recipientId,
        UUID proposerHaveListingId,
        UUID recipientHaveListingId,
        UUID meetupSpotId,
        String status,
        boolean proposerConfirmed,
        boolean recipientConfirmed
    ) {}

    private LockedSwap lockSwap(UUID swapId) {
        return jdbcClient.sql("""
                SELECT id, thread_id, proposer_id, recipient_id,
                       proposer_have_listing_id, recipient_have_listing_id,
                       meetup_spot_id, status, proposer_confirmed, recipient_confirmed
                FROM swaps
                WHERE id = :id
                FOR UPDATE
                """)
            .param("id", swapId)
            .query((rs, rowNum) -> new LockedSwap(
                rs.getObject("id", UUID.class),
                rs.getObject("thread_id", UUID.class),
                rs.getObject("proposer_id", UUID.class),
                rs.getObject("recipient_id", UUID.class),
                rs.getObject("proposer_have_listing_id", UUID.class),
                rs.getObject("recipient_have_listing_id", UUID.class),
                rs.getObject("meetup_spot_id", UUID.class),
                rs.getString("status"),
                rs.getBoolean("proposer_confirmed"),
                rs.getBoolean("recipient_confirmed")
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("SWAP_NOT_FOUND", "Swap not found"));
    }

    private void lockHaveListingsInOrder(UUID listingA, UUID listingB) {
        UUID first = listingA.compareTo(listingB) <= 0 ? listingA : listingB;
        UUID second = listingA.compareTo(listingB) <= 0 ? listingB : listingA;
        jdbcClient.sql("SELECT id FROM have_listings WHERE id = :id FOR UPDATE")
            .param("id", first)
            .query(UUID.class)
            .single();
        if (!first.equals(second)) {
            jdbcClient.sql("SELECT id FROM have_listings WHERE id = :id FOR UPDATE")
                .param("id", second)
                .query(UUID.class)
                .single();
        }
    }

    private record ListingForSwap(UUID id, UUID userId, UUID gameId, String title, String platform, String status) {}

    private ListingForSwap fetchListingForSwap(UUID listingId) {
        return jdbcClient.sql("""
                SELECT h.id, h.user_id, h.game_id, g.title, h.platform, h.status
                FROM have_listings h
                JOIN games g ON g.id = h.game_id
                WHERE h.id = :id
                """)
            .param("id", listingId)
            .query((rs, rowNum) -> new ListingForSwap(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("game_id", UUID.class),
                rs.getString("title"),
                rs.getString("platform"),
                rs.getString("status")
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "HAVE listing " + listingId + " not found"));
    }

    private void emitSwapChatMessage(
        UUID threadId,
        UUID senderId,
        String messageType,
        String body,
        UUID swapId,
        UUID meetupSpotId,
        SwapDto swapDto
    ) {
        UUID msgId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcClient.sql("""
                INSERT INTO chat_messages (id, thread_id, sender_id, message_type, body, swap_id, meetup_spot_id, created_at)
                VALUES (:id, :threadId, :senderId, :messageType, :body, :swapId, :meetupSpotId, :createdAt)
                """)
            .param("id", msgId)
            .param("threadId", threadId)
            .param("senderId", senderId)
            .param("messageType", messageType)
            .param("body", body)
            .param("swapId", swapId)
            .param("meetupSpotId", meetupSpotId)
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
            .param("id", senderId)
            .query((rs, rowNum) -> new SenderInfo(rs.getString("display_name"), rs.getString("avatar_seed")))
            .single();

        ChatMessageDto msgDto = new ChatMessageDto(
            msgId,
            threadId,
            senderId,
            sender.displayName(),
            sender.avatarSeed(),
            messageType,
            body,
            swapId,
            swapDto,
            swapDto != null ? swapDto.meetupSpotId() : null,
            swapDto != null ? swapDto.meetupSpotName() : null,
            swapDto != null ? swapDto.meetupSpotAddress() : null,
            now
        );

        chatPubSubRelay.publishChatMessage(threadId, msgDto);
    }
}
