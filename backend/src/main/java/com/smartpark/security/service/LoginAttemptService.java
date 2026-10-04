package com.smartpark.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic in-memory login brute-force abuse protection.
 *
 * <p>Tracks consecutive failed login attempts by account email. If the threshold
 * is exceeded, the account is temporarily locked for the configured duration.
 *
 * <p><strong>LIMITATION NOTICE:</strong>
 * This in-memory implementation is designed for single-node / development environments.
 * In a distributed, multi-instance production deployment, failure state will not be shared
 * across nodes. A distributed Redis-based rate limiter will replace this service in a later phase.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final int maxAttempts;
    private final long lockoutDurationSeconds;

    private static class AttemptMetadata {
        int attempts;
        Instant lastAttemptTime;
        Instant lockoutUntil;

        AttemptMetadata(Instant now) {
            this.attempts = 1;
            this.lastAttemptTime = now;
            this.lockoutUntil = null;
        }
    }

    private final Map<String, AttemptMetadata> attemptsCache = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.max-login-attempts:5}") int maxAttempts,
            @Value("${app.security.lockout-duration-minutes:15}") int lockoutDurationMinutes
    ) {
        this.maxAttempts = maxAttempts;
        this.lockoutDurationSeconds = lockoutDurationMinutes * 60L;
    }

    public synchronized void recordFailedAttempt(String email) {
        String key = normalizeKey(email);
        Instant now = Instant.now();
        cleanupExpiredEntries();

        attemptsCache.compute(key, (k, meta) -> {
            if (meta == null || isLockoutExpired(meta, now)) {
                return new AttemptMetadata(now);
            }

            meta.attempts++;
            meta.lastAttemptTime = now;

            if (meta.attempts >= maxAttempts) {
                meta.lockoutUntil = now.plusSeconds(lockoutDurationSeconds);
                log.warn("Security Alert: User {} reached {} failed login attempts. Temporarily locked until {}.",
                        key, meta.attempts, meta.lockoutUntil);
            }
            return meta;
        });
    }

    public void recordSuccess(String email) {
        attemptsCache.remove(normalizeKey(email));
    }

    public boolean isLocked(String email) {
        String key = normalizeKey(email);
        AttemptMetadata meta = attemptsCache.get(key);
        if (meta == null || meta.lockoutUntil == null) {
            return false;
        }

        Instant now = Instant.now();
        if (now.isAfter(meta.lockoutUntil)) {
            // Lockout has expired, clean up
            attemptsCache.remove(key);
            return false;
        }

        return true;
    }

    public int getRemainingAttempts(String email) {
        AttemptMetadata meta = attemptsCache.get(normalizeKey(email));
        if (meta == null) {
            return maxAttempts;
        }
        return Math.max(0, maxAttempts - meta.attempts);
    }

    public void reset(String email) {
        attemptsCache.remove(normalizeKey(email));
    }

    private boolean isLockoutExpired(AttemptMetadata meta, Instant now) {
        return meta.lockoutUntil != null && now.isAfter(meta.lockoutUntil);
    }

    private void cleanupExpiredEntries() {
        if (attemptsCache.size() > 5000) {
            Instant now = Instant.now();
            attemptsCache.entrySet().removeIf(entry -> {
                AttemptMetadata meta = entry.getValue();
                return isLockoutExpired(meta, now)
                        || meta.lastAttemptTime.plusSeconds(lockoutDurationSeconds * 2).isBefore(now);
            });
        }
    }

    private String normalizeKey(String key) {
        return key != null ? key.trim().toLowerCase() : "unknown";
    }
}
