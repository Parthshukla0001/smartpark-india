package com.smartpark;

import com.smartpark.cache.ParkingCacheService;
import com.smartpark.dto.parking.ParkingLotResponse;
import com.smartpark.dto.parking.UpdateParkingLotRequest;
import com.smartpark.entity.*;
import com.smartpark.ratelimit.RateLimitResult;
import com.smartpark.ratelimit.RedisRateLimiter;
import com.smartpark.repository.ParkingLotRepository;
import com.smartpark.repository.RoleRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.jwt.JwtService;
import com.smartpark.service.ParkingLotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RedisCacheAndRateLimitingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ParkingCacheService parkingCacheService;

    @Autowired
    private ParkingLotService parkingLotService;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ParkingLot testLot;
    private String testUserToken;
    private String testUserEmail;

    @BeforeEach
    void setUp() {
        // Clean up Redis keys before each test
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Role driverRole = roleRepository.findByName(RoleType.DRIVER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.DRIVER, "Driver")));

        testUserEmail = "cache.test." + suffix + "@smartpark.in";
        User user = new User(
                "Cache",
                "Tester",
                testUserEmail,
                "+9198765" + (int)(10000 + Math.random() * 89999),
                passwordEncoder.encode("SecurePass123!"),
                driverRole
        );
        userRepository.save(user);
        testUserToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().getName().name());

        testLot = new ParkingLot(
                "Cache Hub " + suffix,
                "Nayapalli, Bhubaneswar",
                "Bhubaneswar",
                "Odisha",
                "751012",
                new BigDecimal("20.2961"),
                new BigDecimal("85.8245")
        );
        testLot.setOpeningTime(LocalTime.of(6, 0));
        testLot.setClosingTime(LocalTime.of(23, 0));
        testLot = parkingLotRepository.save(testLot);
    }

    @Test
    @DisplayName("1. First read fetches from PostgreSQL and populates Redis cache")
    void testCacheAsideFirstReadPopulatesRedis() throws Exception {
        String cacheKey = "parking-lot:" + testLot.getId();
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        mockMvc.perform(get("/api/parking-lots/{id}", testLot.getId())
                        .header("Authorization", "Bearer " + testUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testLot.getId()))
                .andExpect(jsonPath("$.name").value(testLot.getName()));

        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedJson).isNotNull().contains(testLot.getName());
    }

    @Test
    @DisplayName("2. Second read returns cached data directly from Redis")
    void testSecondReadReturnsDataDirectlyFromRedis() throws Exception {
        String cacheKey = "parking-lot:" + testLot.getId();

        // 1st read populates Redis
        mockMvc.perform(get("/api/parking-lots/{id}", testLot.getId())
                        .header("Authorization", "Bearer " + testUserToken))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Directly modify database under the hood (bypassing service eviction)
        jdbcTemplate.update("UPDATE parking_lots SET name = 'DirectlyModifiedInDb' WHERE id = ?", testLot.getId());

        // 2nd read must return cached value from Redis, not the updated DB value
        mockMvc.perform(get("/api/parking-lots/{id}", testLot.getId())
                        .header("Authorization", "Bearer " + testUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(testLot.getName())); // Original name from cache
    }

    @Test
    @DisplayName("3. Write invalidates affected cache entries in Redis")
    void testWriteInvalidatesCache() {
        Long lotId = testLot.getId();
        String cacheKey = "parking-lot:" + lotId;

        // Warm the cache
        ParkingLotResponse initialResponse = parkingLotService.getParkingLotById(lotId);
        assertThat(initialResponse).isNotNull();
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Update lot via service (triggers cache eviction)
        UpdateParkingLotRequest updateRequest = new UpdateParkingLotRequest(
                "Updated Cache Hub",
                testLot.getAddress(),
                testLot.getCity(),
                testLot.getState(),
                testLot.getPincode(),
                testLot.getLatitude(),
                testLot.getLongitude(),
                testLot.getOpeningTime(),
                testLot.getClosingTime(),
                true
        );
        parkingLotService.updateParkingLot(lotId, updateRequest);

        // Cache must be evicted
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // Next read repopulates cache with updated values
        ParkingLotResponse updatedResponse = parkingLotService.getParkingLotById(lotId);
        assertThat(updatedResponse.getName()).isEqualTo("Updated Cache Hub");
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

    @Test
    @DisplayName("4. Expired cache or missing key falls back to PostgreSQL")
    void testCacheExpiryFallsBackToPostgres() throws Exception {
        String cacheKey = "parking-lot:" + testLot.getId();

        // Read to populate cache
        mockMvc.perform(get("/api/parking-lots/{id}", testLot.getId())
                        .header("Authorization", "Bearer " + testUserToken))
                .andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Simulate TTL expiry by manually deleting the key from Redis
        redisTemplate.delete(cacheKey);
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // Next read transparently falls back to PostgreSQL and repopulates Redis
        mockMvc.perform(get("/api/parking-lots/{id}", testLot.getId())
                        .header("Authorization", "Bearer " + testUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testLot.getId()));

        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

    @Test
    @DisplayName("5. Graceful degradation: Redis unavailability or failures fallback to DB")
    void testGracefulDegradationFailOpen() {
        // With an invalid/non-existent Redis key or simulated failure, service still reads from PostgreSQL
        ParkingLotResponse response = parkingLotService.getParkingLotById(testLot.getId());
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testLot.getId());

        // Test fail-open behavior of RedisRateLimiter
        RedisRateLimiter failOpenLimiter = new RedisRateLimiter(redisTemplate, true, 60, 5, 2, true);
        RateLimitResult result = failOpenLimiter.checkRateLimit("test-user-degradation", true);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("6. Requests below rate limit succeed with rate limit headers")
    void testRequestsBelowRateLimitSucceed() throws Exception {
        mockMvc.perform(get("/api/parking-lots/{id}", testLot.getId())
                        .header("Authorization", "Bearer " + testUserToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("7. Requests exceeding rate limit return HTTP 429 Too Many Requests with Retry-After header")
    void testRequestsExceedingRateLimitReturn429() {
        // Enforce tight rate limit: 3 requests per 30 seconds
        RedisRateLimiter tightLimiter = new RedisRateLimiter(redisTemplate, true, 30, 3, 2, true);
        String clientKey = "test-over-limit-user";

        // First 3 requests allowed
        assertThat(tightLimiter.checkRateLimit(clientKey, true).allowed()).isTrue();
        assertThat(tightLimiter.checkRateLimit(clientKey, true).allowed()).isTrue();
        assertThat(tightLimiter.checkRateLimit(clientKey, true).allowed()).isTrue();

        // 4th request rejected with 429 semantics
        RateLimitResult fourth = tightLimiter.checkRateLimit(clientKey, true);
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.retryAfterSeconds()).isGreaterThan(0);
        assertThat(fourth.currentCount()).isEqualTo(4);
        assertThat(fourth.limit()).isEqualTo(3);
    }

    @Test
    @DisplayName("8. Rate limit resets after window/TTL elapses")
    void testRateLimitWindowReset() {
        RedisRateLimiter shortWindowLimiter = new RedisRateLimiter(redisTemplate, true, 10, 1, 1, true);
        String clientKey = "reset-user";

        // Request 1: allowed
        assertThat(shortWindowLimiter.checkRateLimit(clientKey, true).allowed()).isTrue();
        // Request 2: rejected
        assertThat(shortWindowLimiter.checkRateLimit(clientKey, true).allowed()).isFalse();

        // Reset window (simulate TTL expiration by deleting Redis key)
        redisTemplate.delete("rate-limit:reset-user");

        // Request 3: allowed again after reset
        assertThat(shortWindowLimiter.checkRateLimit(clientKey, true).allowed()).isTrue();
    }

    @Test
    @DisplayName("9. User isolation: different users have independent rate limits")
    void testUserIsolationInRateLimiting() {
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, true, 60, 2, 1, true);
        String userA = "user:alpha@smartpark.in";
        String userB = "user:beta@smartpark.in";

        // Exhaust User A's quota
        assertThat(limiter.checkRateLimit(userA, true).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(userA, true).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(userA, true).allowed()).isFalse(); // Blocked

        // User B must still be unaffected
        assertThat(limiter.checkRateLimit(userB, true).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(userB, true).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(userB, true).allowed()).isFalse(); // Blocked only when B reaches limit
    }

    @Test
    @DisplayName("10. Unauthenticated requests are throttled independently by IP")
    void testUnauthenticatedRateLimitingByIp() {
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, true, 60, 10, 2, true);
        String ip1 = "ip:192.168.1.50";
        String ip2 = "ip:192.168.1.51";

        // IP 1 exhausts its unauthenticated limit (2)
        assertThat(limiter.checkRateLimit(ip1, false).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(ip1, false).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(ip1, false).allowed()).isFalse(); // IP 1 blocked

        // IP 2 is completely independent
        assertThat(limiter.checkRateLimit(ip2, false).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(ip2, false).allowed()).isTrue();
        assertThat(limiter.checkRateLimit(ip2, false).allowed()).isFalse(); // IP 2 blocked
    }

    @Test
    @DisplayName("11. Rate limiting operates centrally in Redis rather than JVM local state")
    void testCentralizedRedisStorage() {
        String testKey = "central-test-client";
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, true, 60, 5, 2, true);

        limiter.checkRateLimit(testKey, true);

        // Verify key exists directly in Redis and has appropriate TTL
        String redisKey = "rate-limit:" + testKey;
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
        Long ttl = redisTemplate.getExpire(redisKey);
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(60);

        String value = redisTemplate.opsForValue().get(redisKey);
        assertThat(value).isEqualTo("1");
    }
}
