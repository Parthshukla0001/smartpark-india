package com.smartpark.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.dto.common.PageResponse;
import com.smartpark.dto.parking.ParkingLotResponse;
import com.smartpark.dto.parking.ParkingSlotResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cache-aside service for read-heavy parking infrastructure data.
 * Guarantees graceful degradation (fail-open to PostgreSQL if Redis is down)
 * and ensures cache invalidations execute strictly after database transactions commit.
 */
@Service
public class ParkingCacheService {

    private static final Logger log = LoggerFactory.getLogger(ParkingCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean cacheEnabled;
    private final long parkingLotTtlSeconds;
    private final long parkingSlotTtlSeconds;

    public ParkingCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper redisObjectMapper,
            @Value("${smartpark.cache.enabled:true}") boolean cacheEnabled,
            @Value("${smartpark.cache.parking-lot-ttl-seconds:300}") long parkingLotTtlSeconds,
            @Value("${smartpark.cache.parking-slot-ttl-seconds:120}") long parkingSlotTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = redisObjectMapper;
        this.cacheEnabled = cacheEnabled;
        this.parkingLotTtlSeconds = parkingLotTtlSeconds;
        this.parkingSlotTtlSeconds = parkingSlotTtlSeconds;
    }

    // =========================================================================
    // PARKING LOT CACHE-ASIDE READS / WRITES
    // =========================================================================

    public Optional<ParkingLotResponse> getParkingLot(Long id) {
        if (!cacheEnabled) return Optional.empty();
        String key = "parking-lot:" + id;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                ParkingLotResponse response = objectMapper.readValue(json, ParkingLotResponse.class);
                return Optional.of(response);
            }
        } catch (Exception ex) {
            log.warn("Redis cache read failure for key {}: {}. Falling back to PostgreSQL.", key, ex.getMessage());
        }
        return Optional.empty();
    }

    public void putParkingLot(Long id, ParkingLotResponse response) {
        if (!cacheEnabled || response == null) return;
        String key = "parking-lot:" + id;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(parkingLotTtlSeconds));
        } catch (Exception ex) {
            log.warn("Redis cache write failure for key {}: {}", key, ex.getMessage());
        }
    }

    public Optional<PageResponse<ParkingLotResponse>> getParkingLotsPage(String cacheKey) {
        if (!cacheEnabled) return Optional.empty();
        String key = "parking-lots:" + cacheKey;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                PageResponse<ParkingLotResponse> response = objectMapper.readValue(
                        json, new TypeReference<PageResponse<ParkingLotResponse>>() {});
                return Optional.of(response);
            }
        } catch (Exception ex) {
            log.warn("Redis cache read failure for key {}: {}. Falling back to PostgreSQL.", key, ex.getMessage());
        }
        return Optional.empty();
    }

    public void putParkingLotsPage(String cacheKey, PageResponse<ParkingLotResponse> pageResponse) {
        if (!cacheEnabled || pageResponse == null) return;
        String key = "parking-lots:" + cacheKey;
        try {
            String json = objectMapper.writeValueAsString(pageResponse);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(parkingLotTtlSeconds));
        } catch (Exception ex) {
            log.warn("Redis cache write failure for key {}: {}", key, ex.getMessage());
        }
    }

    // =========================================================================
    // PARKING SLOT CACHE-ASIDE READS / WRITES
    // =========================================================================

    public Optional<List<ParkingSlotResponse>> getSlotsByFloor(Long lotId, Long floorId) {
        if (!cacheEnabled) return Optional.empty();
        String key = "parking-slots:" + lotId + ":" + floorId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                List<ParkingSlotResponse> slots = objectMapper.readValue(
                        json, new TypeReference<List<ParkingSlotResponse>>() {});
                return Optional.of(slots);
            }
        } catch (Exception ex) {
            log.warn("Redis cache read failure for key {}: {}. Falling back to PostgreSQL.", key, ex.getMessage());
        }
        return Optional.empty();
    }

    public void putSlotsByFloor(Long lotId, Long floorId, List<ParkingSlotResponse> slots) {
        if (!cacheEnabled || slots == null) return;
        String key = "parking-slots:" + lotId + ":" + floorId;
        try {
            String json = objectMapper.writeValueAsString(slots);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(parkingSlotTtlSeconds));
        } catch (Exception ex) {
            log.warn("Redis cache write failure for key {}: {}", key, ex.getMessage());
        }
    }

    public Optional<ParkingSlotResponse> getSlot(Long lotId, Long floorId, Long slotId) {
        if (!cacheEnabled) return Optional.empty();
        String key = "parking-slot:" + lotId + ":" + floorId + ":" + slotId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                ParkingSlotResponse slot = objectMapper.readValue(json, ParkingSlotResponse.class);
                return Optional.of(slot);
            }
        } catch (Exception ex) {
            log.warn("Redis cache read failure for key {}: {}. Falling back to PostgreSQL.", key, ex.getMessage());
        }
        return Optional.empty();
    }

    public void putSlot(Long lotId, Long floorId, Long slotId, ParkingSlotResponse slot) {
        if (!cacheEnabled || slot == null) return;
        String key = "parking-slot:" + lotId + ":" + floorId + ":" + slotId;
        try {
            String json = objectMapper.writeValueAsString(slot);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(parkingSlotTtlSeconds));
        } catch (Exception ex) {
            log.warn("Redis cache write failure for key {}: {}", key, ex.getMessage());
        }
    }

    // =========================================================================
    // POST-COMMIT CACHE INVALIDATION
    // =========================================================================

    public void evictParkingLot(Long lotId) {
        executeAfterCommit(() -> {
            try {
                // Delete specific lot detail cache
                redisTemplate.delete("parking-lot:" + lotId);
                // Invalidate all lot list queries
                deleteKeysByPattern("parking-lots:*");
            } catch (Exception ex) {
                log.warn("Redis eviction failure for lot id={}: {}", lotId, ex.getMessage());
            }
        });
    }

    public void evictAllParkingLots() {
        executeAfterCommit(() -> {
            try {
                deleteKeysByPattern("parking-lot:*");
                deleteKeysByPattern("parking-lots:*");
            } catch (Exception ex) {
                log.warn("Redis eviction failure for all parking lots: {}", ex.getMessage());
            }
        });
    }

    public void evictSlot(Long lotId, Long floorId, Long slotId) {
        executeAfterCommit(() -> {
            try {
                redisTemplate.delete("parking-slot:" + lotId + ":" + floorId + ":" + slotId);
                redisTemplate.delete("parking-slots:" + lotId + ":" + floorId);
            } catch (Exception ex) {
                log.warn("Redis eviction failure for slot id={}: {}", slotId, ex.getMessage());
            }
        });
    }

    public void evictFloorSlots(Long lotId, Long floorId) {
        executeAfterCommit(() -> {
            try {
                redisTemplate.delete("parking-slots:" + lotId + ":" + floorId);
                deleteKeysByPattern("parking-slot:" + lotId + ":" + floorId + ":*");
            } catch (Exception ex) {
                log.warn("Redis eviction failure for floor id={}: {}", floorId, ex.getMessage());
            }
        });
    }

    private void deleteKeysByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void executeAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
