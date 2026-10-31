package com.gameswap.games;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GameDtos {

    private GameDtos() {}

    public record GameDto(
        UUID id,
        long igdbId,
        String slug,
        String title,
        String coverUrl,
        String summary,
        Integer releaseYear,
        List<String> genres,
        List<String> supportedPlatforms,
        BigDecimal avgReviewRating,
        int reviewCount,
        int activeLocalCopiesCount
    ) {}

    public record LocalGameOwnerDto(
        UUID listingId,
        UUID userId,
        String displayName,
        String avatarSeed,
        String localityName,
        BigDecimal userRatingAvg,
        int userRatingCount,
        String platform,
        String condition,
        String notes,
        Double distanceKm,
        Instant listedAt
    ) {}

    public record GameReviewDto(
        UUID id,
        UUID gameId,
        UUID userId,
        String userDisplayName,
        String userAvatarSeed,
        String userLocalityName,
        int rating,
        String comment,
        Instant createdAt
    ) {}

    public record CreateGameReviewRequestDto(
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @NotBlank(message = "Review comment is required")
        @Size(min = 3, max = 500, message = "Review comment must be 3 to 500 characters")
        String comment
    ) {}

    public record GameDiscussionDto(
        UUID id,
        UUID gameId,
        UUID userId,
        String userDisplayName,
        String userAvatarSeed,
        String userLocalityName,
        String body,
        Instant createdAt
    ) {}

    public record CreateGameDiscussionRequestDto(
        @NotBlank(message = "Discussion post cannot be empty")
        @Size(min = 2, max = 1000, message = "Discussion post must be 2 to 1000 characters")
        String body
    ) {}
}

