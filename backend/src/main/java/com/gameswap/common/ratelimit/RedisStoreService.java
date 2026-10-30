package com.gameswap.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RedisStoreService {

    private static final Logger log = LoggerFactory.getLogger(RedisStoreService.class);

    private final StringRedisTemplate redisTemplate;

    private record LocalEntry(String value, Instant expiresAt) {}
    private record LocalCounter(AtomicLong count, Instant expiresAt) {}

    private final Map<String, LocalEntry> fallbackStore = new ConcurrentHashMap<>();
    private final Map<String, LocalCounter> fallbackCounters = new ConcurrentHashMap<>();

    public RedisStoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            return;
        } catch (Exception e) {
            log.debug("Redis unavailable for SET {}, using local fallback: {}", key, e.getMessage());
        }
        fallbackStore.put(key, new LocalEntry(value, Instant.now().plus(ttl)));
    }

    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.debug("Redis unavailable for GET {}, using local fallback", key);
        }
        LocalEntry entry = fallbackStore.get(key);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            fallbackStore.remove(key);
            return null;
        }
        return entry.value();
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Redis unavailable for DEL {}, using local fallback", key);
        }
        fallbackStore.remove(key);
        fallbackCounters.remove(key);
    }

    public void deleteByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("Redis unavailable for DEL prefix {}, using local fallback", prefix);
        }
        fallbackStore.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public long incrementWithWindow(String key, Duration window) {
        try {
            Long val = redisTemplate.opsForValue().increment(key);
            if (val != null && val == 1L) {
                redisTemplate.expire(key, window);
            }
            return val != null ? val : 1L;
        } catch (Exception e) {
            log.debug("Redis unavailable for INCR {}, using local fallback", key);
        }
        Instant now = Instant.now();
        LocalCounter counter = fallbackCounters.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.expiresAt())) {
                return new LocalCounter(new AtomicLong(1L), now.plus(window));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return counter.count().get();
    }

    public void clearAll() {
        try {
            var factory = redisTemplate.getConnectionFactory();
            if (factory != null) {
                try (var conn = factory.getConnection()) {
                    conn.serverCommands().flushDb();
                }
            }
        } catch (Exception ignored) {
        }
        fallbackStore.clear();
        fallbackCounters.clear();
    }
}
