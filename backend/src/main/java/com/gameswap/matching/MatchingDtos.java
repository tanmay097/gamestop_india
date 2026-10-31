package com.gameswap.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MatchingDtos {

    private MatchingDtos() {}

    public record MatchedGameItemDto(
        UUID listingId,
        UUID gameId,
        String title,
        String coverUrl,
        String platform,
        String condition
    ) {}

    public record MatchCardDto(
        UUID userId,
        String displayName,
        String avatarSeed,
        String localityName,
        List<String> consoles,
        BigDecimal ratingAvg,
        int ratingCount,
        int completedSwapsCount,
        int matchTier,
        String matchType,
        double distanceKm,
        double distanceMeters,
        List<MatchedGameItemDto> theyHaveGamesIWant,
        List<MatchedGameItemDto> theyWantGamesIHave,
        List<MatchedGameItemDto> otherHaveListings
    ) {}

    public record MapPinDto(
        UUID listingId,
        UUID userId,
        String userDisplayName,
        String userAvatarSeed,
        String localityName,
        BigDecimal userRatingAvg,
        UUID gameId,
        String gameTitle,
        String gameCoverUrl,
        String platform,
        String condition,
        double fuzzedLatitude,
        double fuzzedLongitude,
        double distanceKm,
        int matchTier,
        Instant listedAt
    ) {}
}

