package com.gameswap.ratings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class RatingDtos {

    private RatingDtos() {}

    public record CreateRatingRequestDto(
        @NotNull(message = "Star rating is required")
        @Min(value = 1, message = "Stars must be between 1 and 5")
        @Max(value = 5, message = "Stars must be between 1 and 5")
        Integer stars,

        @Size(max = 300, message = "Comment cannot exceed 300 characters")
        String comment
    ) {}

    public record UserRatingDto(
        UUID id,
        UUID swapId,
        UUID raterId,
        String raterDisplayName,
        String raterAvatarSeed,
        String raterLocalityName,
        UUID rateeId,
        int stars,
        String comment,
        Instant createdAt
    ) {}
}

