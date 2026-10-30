package com.gameswap.common.api;

import com.gameswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public final class CursorCodec {

    private static final String SEP = "|";
    private static final String SEP_REGEX = "\\|";

    private CursorCodec() {}

    public record TimeCursor(Instant timestamp, UUID id) {}

    public record StringCursor(String value, UUID id) {}

    public record MatchCursor(int matchTier, double distanceMeters, UUID userId) {}

    public record DistanceCursor(double distanceMeters, UUID id) {}

    public static String encodeTime(Instant timestamp, UUID id) {
        String raw = timestamp.toEpochMilli() + SEP + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TimeCursor decodeTime(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(SEP_REGEX, 2);
            return new TimeCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Malformed pagination cursor");
        }
    }

    public static String encodeString(String value, UUID id) {
        String raw = value + SEP + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static StringCursor decodeString(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int idx = raw.lastIndexOf(SEP);
            return new StringCursor(raw.substring(0, idx), UUID.fromString(raw.substring(idx + 1)));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Malformed pagination cursor");
        }
    }

    public static String encodeMatch(int matchTier, double distanceMeters, UUID userId) {
        String raw = matchTier + SEP + String.format("%.4f", distanceMeters) + SEP + userId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static MatchCursor decodeMatch(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(SEP_REGEX, 3);
            return new MatchCursor(
                Integer.parseInt(parts[0]),
                Double.parseDouble(parts[1]),
                UUID.fromString(parts[2])
            );
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Malformed match pagination cursor");
        }
    }

    public static String encodeDistance(double distanceMeters, UUID id) {
        String raw = String.format("%.4f", distanceMeters) + SEP + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static DistanceCursor decodeDistance(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(SEP_REGEX, 2);
            return new DistanceCursor(Double.parseDouble(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Malformed distance pagination cursor");
        }
    }

    public static int clampLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }
}
