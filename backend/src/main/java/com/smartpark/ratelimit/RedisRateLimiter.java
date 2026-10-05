package com.smartpark.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Distributed rate limiter powered by Redis using an atomic fixed-window Lua script.
 * Enforces independent rate limits for authenticated users vs unauthenticated client IPs.
 * Configured with graceful degradation (fail-open) if Redis is unavailable.
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])

        local current = redis.call('INCR', key)
        if current == 1 then
            redis.call('EXPIRE', key, window)
        end

        local ttl = redis.call('TTL', key)
        return {current, ttl}
        """;

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script;
    private final boolean enabled;
    private final long windowSeconds;
    private final long authenticatedLimit;
    private final long unauthenticatedLimit;
    private final boolean failOpen;

    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${smartpark.rate-limit.enabled:true}") boolean enabled,
            @Value("${smartpark.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${smartpark.rate-limit.authenticated-limit:100}") long authenticatedLimit,
            @Value("${smartpark.rate-limit.unauthenticated-limit:20}") long unauthenticatedLimit,
            @Value("${smartpark.rate-limit.fail-open:true}") boolean failOpen
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.windowSeconds = windowSeconds;
        this.authenticatedLimit = authenticatedLimit;
        this.unauthenticatedLimit = unauthenticatedLimit;
        this.failOpen = failOpen;

        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(LUA_SCRIPT);
        this.script.setResultType(List.class);
    }

    public RateLimitResult checkRateLimit(String clientKey, boolean isAuthenticated) {
        if (!enabled) {
            return RateLimitResult.allow(0, Long.MAX_VALUE);
        }

        long limit = isAuthenticated ? authenticatedLimit : unauthenticatedLimit;
        String fullKey = "rate-limit:" + clientKey;

        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResult = redisTemplate.execute(
                    script,
                    Collections.singletonList(fullKey),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds)
            );

            if (rawResult != null && rawResult.size() >= 2) {
                long current = ((Number) rawResult.get(0)).longValue();
                long ttl = ((Number) rawResult.get(1)).longValue();
                long retryAfter = ttl > 0 ? ttl : windowSeconds;

                if (current > limit) {
                    return RateLimitResult.reject(current, limit, retryAfter);
                } else {
                    return RateLimitResult.allow(current, limit);
                }
            }
        } catch (Exception ex) {
            log.warn("Redis rate limiter check failed for key {}: {}. Fail-open policy={}",
                    fullKey, ex.getMessage(), failOpen);
            if (failOpen) {
                return RateLimitResult.allow(0, limit);
            } else {
                return RateLimitResult.reject(limit + 1, limit, windowSeconds);
            }
        }

        return RateLimitResult.allow(1, limit);
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public long getAuthenticatedLimit() {
        return authenticatedLimit;
    }

    public long getUnauthenticatedLimit() {
        return unauthenticatedLimit;
    }
}
