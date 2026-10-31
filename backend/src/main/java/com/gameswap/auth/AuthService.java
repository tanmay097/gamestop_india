package com.gameswap.auth;

import com.gameswap.auth.AuthDtos.*;
import com.gameswap.common.config.GameSwapProperties;
import com.gameswap.common.error.ApiException;
import com.gameswap.common.ratelimit.RateLimiterService;
import com.gameswap.common.ratelimit.RedisStoreService;
import com.gameswap.common.security.JwtService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final PhoneCryptoService phoneCryptoService;
    private final SmsOtpProvider smsOtpProvider;
    private final RedisStoreService redisStore;
    private final RateLimiterService rateLimiterService;
    private final JwtService jwtService;
    private final JdbcClient jdbcClient;
    private final GameSwapProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        PhoneCryptoService phoneCryptoService,
        SmsOtpProvider smsOtpProvider,
        RedisStoreService redisStore,
        RateLimiterService rateLimiterService,
        JwtService jwtService,
        JdbcClient jdbcClient,
        GameSwapProperties properties
    ) {
        this.phoneCryptoService = phoneCryptoService;
        this.smsOtpProvider = smsOtpProvider;
        this.redisStore = redisStore;
        this.rateLimiterService = rateLimiterService;
        this.jwtService = jwtService;
        this.jdbcClient = jdbcClient;
        this.properties = properties;
    }

    public OtpRequestResponseDto requestOtp(String rawPhone) {
        String normalizedPhone = phoneCryptoService.normalizePhone(rawPhone);
        rateLimiterService.checkOtpRateLimit(normalizedPhone);

        String phoneHash = phoneCryptoService.hashPhone(normalizedPhone);
        String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        long ttlSeconds = properties.auth().otpTtlSeconds();

        redisStore.set("otp:" + phoneHash, otpCode, Duration.ofSeconds(ttlSeconds));
        smsOtpProvider.sendOtp(normalizedPhone, otpCode);

        String devHint = properties.auth().exposeDevOtpInResponse() ? otpCode : null;
        return new OtpRequestResponseDto(
            "OTP sent successfully",
            ttlSeconds,
            smsOtpProvider.providerName(),
            devHint
        );
    }

    @Transactional
    public AuthTokensResponseDto verifyOtp(String rawPhone, String otp) {
        String normalizedPhone = phoneCryptoService.normalizePhone(rawPhone);
        String phoneHash = phoneCryptoService.hashPhone(normalizedPhone);
        String otpKey = "otp:" + phoneHash;

        String expectedOtp = redisStore.get(otpKey);
        if (expectedOtp == null || !expectedOtp.equals(otp)) {
            throw ApiException.unauthorized("INVALID_OTP", "Invalid or expired OTP code");
        }
        redisStore.delete(otpKey);

        UserAuthRecord user = findOrCreateUser(normalizedPhone, phoneHash);
        if (user.isBanned()) {
            throw ApiException.forbidden("USER_BANNED", "Your account has been suspended");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthTokensResponseDto refreshTokens(String rawRefreshToken) {
        String tokenHash = jwtService.hashRefreshToken(rawRefreshToken);

        record TokenRow(UUID id, UUID userId, Instant expiresAt, boolean revoked) {}
        Optional<TokenRow> rowOpt = jdbcClient.sql("""
                SELECT id, user_id, expires_at, revoked
                FROM refresh_tokens
                WHERE token_hash = :tokenHash
                FOR UPDATE
                """)
            .param("tokenHash", tokenHash)
            .query((rs, rowNum) -> new TokenRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getBoolean("revoked")
            ))
            .optional();

        TokenRow row = rowOpt.orElseThrow(() ->
            ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid")
        );

        if (row.revoked() || Instant.now().isAfter(row.expiresAt())) {
            throw ApiException.unauthorized("EXPIRED_REFRESH_TOKEN", "Refresh token has expired or been revoked");
        }

        // Rotate refresh token
        jdbcClient.sql("UPDATE refresh_tokens SET revoked = true WHERE id = :id")
            .param("id", row.id())
            .update();

        UserAuthRecord user = findUserById(row.userId());
        if (user.isBanned()) {
            throw ApiException.forbidden("USER_BANNED", "Your account has been suspended");
        }

        return issueTokens(user);
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = jwtService.hashRefreshToken(rawRefreshToken);
            jdbcClient.sql("UPDATE refresh_tokens SET revoked = true WHERE token_hash = :tokenHash AND user_id = :userId")
                .param("tokenHash", tokenHash)
                .param("userId", userId)
                .update();
        } else {
            jdbcClient.sql("UPDATE refresh_tokens SET revoked = true WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        }
    }

    private AuthTokensResponseDto issueTokens(UserAuthRecord user) {
        String accessToken = jwtService.generateAccessToken(user.id(), user.isAdmin());
        String rawRefreshToken = jwtService.generateRawRefreshToken();
        String refreshTokenHash = jwtService.hashRefreshToken(rawRefreshToken);
        Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshExpirationSeconds());

        jdbcClient.sql("""
                INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
                VALUES (:userId, :tokenHash, :expiresAt)
                """)
            .param("userId", user.id())
            .param("tokenHash", refreshTokenHash)
            .param("expiresAt", Timestamp.from(expiresAt))
            .update();

        return new AuthTokensResponseDto(
            accessToken,
            rawRefreshToken,
            jwtService.getAccessExpirationSeconds(),
            user.id(),
            user.displayName(),
            user.onboardingCompleted()
        );
    }

    private record UserAuthRecord(
        UUID id,
        String displayName,
        boolean onboardingCompleted,
        boolean isAdmin,
        boolean isBanned
    ) {}

    private UserAuthRecord findOrCreateUser(String normalizedPhone, String phoneHash) {
        Optional<UserAuthRecord> existing = jdbcClient.sql("""
                SELECT id, display_name, onboarding_completed, is_admin, is_banned
                FROM users
                WHERE phone_hash = :phoneHash
                """)
            .param("phoneHash", phoneHash)
            .query((rs, rowNum) -> new UserAuthRecord(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getBoolean("onboarding_completed"),
                rs.getBoolean("is_admin"),
                rs.getBoolean("is_banned")
            ))
            .optional();

        if (existing.isPresent()) {
            return existing.get();
        }

        UUID newId = UUID.randomUUID();
        String encryptedPhone = phoneCryptoService.encryptPhone(normalizedPhone);
        String avatarSeed = "gamer-" + newId.toString().substring(0, 8);

        jdbcClient.sql("""
                INSERT INTO users (id, phone_hash, phone_encrypted, avatar_seed)
                VALUES (:id, :phoneHash, :phoneEncrypted, :avatarSeed)
                """)
            .param("id", newId)
            .param("phoneHash", phoneHash)
            .param("phoneEncrypted", encryptedPhone)
            .param("avatarSeed", avatarSeed)
            .update();

        return new UserAuthRecord(newId, null, false, false, false);
    }

    private UserAuthRecord findUserById(UUID userId) {
        return jdbcClient.sql("""
                SELECT id, display_name, onboarding_completed, is_admin, is_banned
                FROM users
                WHERE id = :id
                """)
            .param("id", userId)
            .query((rs, rowNum) -> new UserAuthRecord(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getBoolean("onboarding_completed"),
                rs.getBoolean("is_admin"),
                rs.getBoolean("is_banned")
            ))
            .optional()
            .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }
}
