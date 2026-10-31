package com.gameswap.swaps;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class SwapDtos {

    private SwapDtos() {}

    public record ProposeSwapRequestDto(
        @NotNull(message = "Chat thread ID is required")
        UUID threadId,

        @NotNull(message = "Your HAVE listing ID is required")
        UUID myHaveListingId,

        @NotNull(message = "Recipient HAVE listing ID is required")
        UUID theirHaveListingId,

        UUID meetupSpotId,

        Instant meetupTime,

        String message
    ) {}

    public record SwapListingSummaryDto(
        UUID listingId,
        UUID ownerId,
        String ownerDisplayName,
        UUID gameId,
        String gameTitle,
        String gameCoverUrl,
        String platform,
        String condition
    ) {}

    public record SwapDto(
        UUID id,
        UUID threadId,
        UUID proposerId,
        String proposerDisplayName,
        UUID recipientId,
        String recipientDisplayName,
        SwapListingSummaryDto proposerListing,
        SwapListingSummaryDto recipientListing,
        UUID meetupSpotId,
        String meetupSpotName,
        String meetupSpotAddress,
        Instant meetupTime,
        String status,
        boolean proposerConfirmed,
        boolean recipientConfirmed,
        boolean myRatingSubmitted,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
    ) {}
}

