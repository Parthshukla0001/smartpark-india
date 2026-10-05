package com.smartpark.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis.
 *
 * Dependency Classification: NON-CRITICAL (DEGRADED / FAIL-OPEN).
 * SmartPark uses Redis for read-through caching and rate limiting.
 * If Redis becomes unavailable:
 * - Read queries fall back to PostgreSQL.
 * - Rate limiter operates in fail-open mode to preserve service availability.
 * Therefore, Redis unavailability reports DEGRADED status without causing
 * the entire application health check to report DOWN.
 */
@Component("redis")
public class SmartParkRedisHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SmartParkRedisHealthIndicator.class);
    private final RedisConnectionFactory redisConnectionFactory;

    @Autowired
    public SmartParkRedisHealthIndicator(@Autowired(required = false) RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        if (redisConnectionFactory == null) {
            return Health.up()
                    .withDetail("status", "DISABLED")
                    .withDetail("mode", "FAIL_OPEN")
                    .withDetail("message", "Redis connection factory not configured")
                    .build();
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pingResponse = connection.ping();
            return Health.up()
                    .withDetail("status", "AVAILABLE")
                    .withDetail("ping", pingResponse)
                    .build();
        } catch (Exception e) {
            log.warn("Redis health check failed (non-critical, fail-open active): {}", e.getMessage());
            return Health.up()
                    .withDetail("status", "DEGRADED")
                    .withDetail("mode", "FAIL_OPEN")
                    .withDetail("message", "Redis unreachable; caching bypassed and rate limiting fails open")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
