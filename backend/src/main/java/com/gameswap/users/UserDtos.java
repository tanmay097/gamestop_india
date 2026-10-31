package com.gameswap.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {}

    public record LocalityDto(
        UUID id,
        String name,
        String pincode,
        double latitude,
        double longitude
    ) {}

    public record MeetupSpotDto(
        UUID id,
        String name,
        String category,
        UUID localityId,
        String localityName,
        String addressHint,
        double latitude,
        double longitude
    ) {}

    public record OnboardingRequestDto(
        @NotBlank(message = "Display name is required")
        @Size(min = 3, max = 30, message = "Display name must be 3 to 30 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Display name can only contain letters, numbers, underscores, dots, and hyphens")
        String displayName,

        @NotEmpty(message = "Select at least one console you own")
        List<String> consoles,

        @NotNull(message = "Bangalore locality is required")
        UUID localityId,

        @Size(max = 280, message = "Bio cannot exceed 280 characters")
        String bio
    ) {}

    public record UpdateProfileRequestDto(
        @Size(min = 3, max = 30, message = "Display name must be 3 to 30 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Display name can only contain letters, numbers, underscores, dots, and hyphens")
        String displayName,

        List<String> consoles,

        UUID localityId,

        @Size(max = 280, message = "Bio cannot exceed 280 characters")
        String bio
    ) {}

    public record MyProfileDto(
        UUID id,
        String displayName,
        String avatarSeed,
        String bio,
        List<String> consoles,
        UUID localityId,
        String localityName,
        BigDecimal ratingAvg,
        int ratingCount,
        int completedSwapsCount,
        boolean onboardingCompleted,
        int activeHaveCount,
        int activeWantCount,
        int unreadNotificationsCount,
        Instant createdAt
    ) {}

    public record ShelfGameDto(
        UUID gameId,
        String title,
        String coverUrl,
        String platform,
        String shelfStatus,
        Instant updatedAt
    ) {}

    public record ProfileListingItemDto(
        UUID listingId,
        UUID gameId,
        String title,
        String coverUrl,
        String platform,
        String condition,
        String notes,
        Instant createdAt
    ) {}

    public record PublicUserProfileDto(
        UUID id,
        String displayName,
        String avatarSeed,
        String bio,
        List<String> consoles,
        String localityName,
        BigDecimal ratingAvg,
        int ratingCount,
        int completedSwapsCount,
        List<ShelfGameDto> currentlyPlaying,
        List<ShelfGameDto> finishedGames,
        List<ProfileListingItemDto> haveListings,
        List<ProfileListingItemDto> wantListings,
        Instant joinedAt
    ) {}

    public record NotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        UUID referenceId,
        boolean isRead,
        Instant createdAt
    ) {}
}
