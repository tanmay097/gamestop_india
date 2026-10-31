package com.gameswap.chat;

import com.gameswap.swaps.SwapDtos.SwapDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {}

    public record OpenThreadRequestDto(
        @NotNull(message = "Recipient user ID is required")
        UUID recipientId
    ) {}

    public record ChatThreadDto(
        UUID id,
        UUID otherUserId,
        String otherUserDisplayName,
        String otherUserAvatarSeed,
        String otherUserLocalityName,
        BigDecimal otherUserRatingAvg,
        String lastMessagePreview,
        Instant lastMessageAt,
        Instant createdAt
    ) {}

    public record SendMessageRequestDto(
        @NotBlank(message = "Message body cannot be empty")
        @Size(min = 1, max = 1000, message = "Message body must be 1 to 1000 characters")
        String body,

        UUID meetupSpotId
    ) {}

    public record ChatMessageDto(
        UUID id,
        UUID threadId,
        UUID senderId,
        String senderDisplayName,
        String senderAvatarSeed,
        String messageType,
        String body,
        UUID swapId,
        SwapDto swap,
        UUID meetupSpotId,
        String meetupSpotName,
        String meetupSpotAddress,
        Instant createdAt
    ) {}

    public record RedisChatEvent(
        UUID threadId,
        ChatMessageDto message
    ) {}
}

