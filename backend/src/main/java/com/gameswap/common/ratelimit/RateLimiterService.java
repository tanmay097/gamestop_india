package com.gameswap.common.ratelimit;

import com.gameswap.common.config.GameSwapProperties;
import com.gameswap.common.error.ApiException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RateLimiterService {

    private final RedisStoreService redisStore;
    private final GameSwapProperties properties;

    public RateLimiterService(RedisStoreService redisStore, GameSwapProperties properties) {
        this.redisStore = redisStore;
        this.properties = properties;
    }

    public void checkOtpRateLimit(String normalizedPhone) {
        int maxRequests = properties.limits().otpRequestsPer10m();
        String key = "rl:otp:" + normalizedPhone;
        long count = redisStore.incrementWithWindow(key, Duration.ofMinutes(10));
        if (count > maxRequests) {
            throw ApiException.tooManyRequests(
                "OTP_RATE_LIMIT_EXCEEDED",
                "Too many OTP requests for this phone number. Please try again in 10 minutes."
            );
        }
    }

    public void checkChatMessageRateLimit(UUID userId) {
        int maxMessages = properties.limits().messagesPerMinute();
        String key = "rl:msg:" + userId;
        long count = redisStore.incrementWithWindow(key, Duration.ofMinutes(1));
        if (count > maxMessages) {
            throw ApiException.tooManyRequests(
                "MESSAGE_RATE_LIMIT_EXCEEDED",
                "You are sending messages too quickly. Please wait a moment."
            );
        }
    }

    public void checkWantMutationRateLimit(UUID userId) {
        int maxMutations = properties.limits().wantsMutationsPerMinute();
        String key = "rl:want:" + userId;
        long count = redisStore.incrementWithWindow(key, Duration.ofMinutes(1));
        if (count > maxMutations) {
            throw ApiException.tooManyRequests(
                "WANT_RATE_LIMIT_EXCEEDED",
                "Too many want list changes in a short period. Please slow down."
            );
        }
    }
}
