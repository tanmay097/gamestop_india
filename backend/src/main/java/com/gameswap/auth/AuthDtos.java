package com.gameswap.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record OtpRequestDto(
        @NotBlank(message = "Phone number is required")
        String phone
    ) {}

    public record OtpRequestResponseDto(
        String message,
        long expiresInSeconds,
        String provider,
        String devOtpHint
    ) {}

    public record OtpVerifyDto(
        @NotBlank(message = "Phone number is required")
        String phone,
        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        String otp
    ) {}

    public record RefreshTokenRequestDto(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
    ) {}

    public record AuthTokensResponseDto(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID userId,
        String displayName,
        boolean onboardingCompleted
    ) {}
}
