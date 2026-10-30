package com.gameswap.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gameswap")
public record GameSwapProperties(
    Auth auth,
    Igdb igdb,
    Matching matching,
    Limits limits,
    Cors cors
) {
    public record Auth(
        String jwtSecret,
        long accessExpirationSeconds,
        long refreshExpirationSeconds,
        String phoneHashSecret,
        String phoneEncryptionSecret,
        String otpProvider,
        long otpTtlSeconds,
        boolean exposeDevOtpInResponse
    ) {}

    public record Igdb(
        String clientId,
        String clientSecret,
        long cacheTtlSeconds
    ) {}

    public record Matching(
        double defaultRadiusKm,
        double maxRadiusKm,
        long cacheTtlSeconds
    ) {}

    public record Limits(
        int maxActiveWants,
        int otpRequestsPer10m,
        int messagesPerMinute,
        int wantsMutationsPerMinute
    ) {}

    public record Cors(
        List<String> allowedOrigins
    ) {}
}
