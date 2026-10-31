package com.gameswap.users;

import com.gameswap.common.error.ApiException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum ConsolePlatform {
    PS4,
    PS5,
    XBOX_ONE,
    SERIES_X_S,
    SWITCH;

    private static final Set<String> VALID_NAMES = Arrays.stream(values())
        .map(Enum::name)
        .collect(Collectors.toSet());

    public static String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("INVALID_PLATFORM", "Console platform is required");
        }
        String upper = raw.trim().toUpperCase();
        if (!VALID_NAMES.contains(upper)) {
            throw ApiException.badRequest(
                "INVALID_PLATFORM",
                "Invalid console platform '" + raw + "'. Allowed: PS4, PS5, XBOX_ONE, SERIES_X_S, SWITCH"
            );
        }
        return upper;
    }

    public static List<String> validateList(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            throw ApiException.badRequest("INVALID_PLATFORMS", "At least one console platform must be selected");
        }
        return platforms.stream()
            .map(ConsolePlatform::normalizeAndValidate)
            .distinct()
            .toList();
    }
}
