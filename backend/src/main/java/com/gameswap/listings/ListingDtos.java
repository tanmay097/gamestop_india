package com.gameswap.listings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ListingDtos {

    private ListingDtos() {}

    public record HaveListingDto(
        UUID id,
        UUID userId,
        UUID gameId,
        String gameTitle,
        String gameCoverUrl,
        String platform,
        String condition,
        String notes,
        String status,
        Instant createdAt
    ) {}

    public record CreateHaveListingRequestDto(
        @NotNull(message = "Game ID is required")
        UUID gameId,

        @NotBlank(message = "Console platform is required")
        String platform,

        @NotBlank(message = "Physical condition is required (LIKE_NEW, GOOD, WORN)")
        String condition,

        @Size(max = 200, message = "Notes cannot exceed 200 characters")
        String notes
    ) {}

    public record WantListingDto(
        UUID id,
        UUID userId,
        UUID gameId,
        String gameTitle,
        String gameCoverUrl,
        String platform,
        String status,
        Instant createdAt
    ) {}

    public record CreateWantListingRequestDto(
        @NotNull(message = "Game ID is required")
        UUID gameId,

        @NotBlank(message = "Console platform is required")
        String platform
    ) {}

    public record ShelfUpdateRequestDto(
        @NotBlank(message = "Console platform is required")
        String platform,

        @NotBlank(message = "Shelf status is required (CURRENTLY_PLAYING or FINISHED)")
        String shelfStatus
    ) {}
}

