package com.gameswap.common.security;

import com.gameswap.common.config.GameSwapProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(GameSwapProperties properties) {
        byte[] keyBytes = properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpirationSeconds = properties.auth().accessExpirationSeconds();
        this.refreshExpirationSeconds = properties.auth().refreshExpirationSeconds();
    }

    public String generateAccessToken(UUID userId, boolean isAdmin) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("admin", isAdmin)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessExpirationSeconds)))
            .signWith(signingKey)
            .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        boolean isAdmin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
        return new AuthenticatedUser(userId, isAdmin);
    }

    public String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }
}
