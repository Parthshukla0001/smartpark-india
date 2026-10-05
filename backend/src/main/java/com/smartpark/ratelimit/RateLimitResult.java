package com.smartpark.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long currentCount,
        long limit,
        long retryAfterSeconds
) {
    public static RateLimitResult allow(long currentCount, long limit) {
        return new RateLimitResult(true, currentCount, limit, 0);
    }

    public static RateLimitResult reject(long currentCount, long limit, long retryAfterSeconds) {
        return new RateLimitResult(false, currentCount, limit, retryAfterSeconds);
    }
}
