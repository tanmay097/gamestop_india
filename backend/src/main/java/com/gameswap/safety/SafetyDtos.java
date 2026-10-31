package com.gameswap.safety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class SafetyDtos {

    private SafetyDtos() {}

    public record BlockedUserDto(
        UUID id,
        UUID blockedUserId,
        String displayName,
        String avatarSeed,
        String localityName,
        Instant blockedAt
    ) {}

    public record ReportUserRequestDto(
        @NotNull(message = "Reported user ID is required")
        UUID reportedUserId,

        @NotBlank(message = "Reason is required")
        @Size(max = 50, message = "Reason cannot exceed 50 characters")
        String reason,

        @Size(max = 500, message = "Details cannot exceed 500 characters")
        String details
    ) {}

    public record UserReportDto(
        UUID id,
        UUID reporterId,
        UUID reportedUserId,
        String reason,
        String details,
        String status,
        Instant createdAt
    ) {}
}
