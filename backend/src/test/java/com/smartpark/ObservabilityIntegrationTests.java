package com.smartpark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.dto.booking.CreateBookingRequest;
import com.smartpark.entity.*;
import com.smartpark.observability.CorrelationIdFilter;
import com.smartpark.observability.SmartParkKafkaHealthIndicator;
import com.smartpark.observability.SmartParkMetrics;
import com.smartpark.observability.SmartParkRedisHealthIndicator;
import com.smartpark.repository.*;
import com.smartpark.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SmartParkMetrics metrics;

    @Autowired
    private SmartParkRedisHealthIndicator redisHealthIndicator;

    @Autowired
    private SmartParkKafkaHealthIndicator kafkaHealthIndicator;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingFloorRepository parkingFloorRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private User adminUser;
    private User driverUser;
    private String adminToken;
    private String driverToken;
    private Vehicle driverVehicle;
    private ParkingLot parkingLot;
    private ParkingFloor floor;
    private ParkingSlot slot;
    private final ZoneId istZone = ZoneId.of("Asia/Kolkata");
    private LocalDate testDay;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();

        adminUser = userRepository.saveAndFlush(new User(
                "Pooja", "Admin", "admin.obs@smartpark.in",
                "+919876543290", passwordEncoder.encode("Password123!"), adminRole
        ));

        driverUser = userRepository.saveAndFlush(new User(
                "Aarav", "Driver", "driver.obs@smartpark.in",
                "+919876543291", passwordEncoder.encode("Password123!"), driverRole
        ));

        adminToken = jwtService.generateToken(adminUser.getId(), adminUser.getEmail(), RoleType.ADMIN.name());
        driverToken = jwtService.generateToken(driverUser.getId(), driverUser.getEmail(), RoleType.DRIVER.name());

        driverVehicle = vehicleRepository.saveAndFlush(new Vehicle(driverUser, "KA01OBS9999", VehicleType.CAR));

        parkingLot = new ParkingLot("Smart City Terminal", "Master Canteen", "Bhubaneswar", "Odisha",
                "751001", new BigDecimal("20.2600000"), new BigDecimal("85.8300000"));
        parkingLot.setOpeningTime(LocalTime.of(6, 0));
        parkingLot.setClosingTime(LocalTime.of(23, 0));
        parkingLot.setActive(true);
        parkingLot = parkingLotRepository.saveAndFlush(parkingLot);

        floor = parkingFloorRepository.saveAndFlush(new ParkingFloor(parkingLot, 1, "Level 1"));
        slot = parkingSlotRepository.saveAndFlush(new ParkingSlot(floor, "OBS-101", SlotType.REGULAR, SlotStatus.AVAILABLE));

        testDay = LocalDate.now(istZone).plusDays(2);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        bookingRepository.deleteAll();
        vehicleRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingFloorRepository.deleteAll();
        parkingLotRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Public /actuator/health is accessible without authentication and returns status UP")
    void testPublicHealthEndpoint_isAccessibleAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                // Ensure unauthenticated access does NOT expose sensitive components details
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("Authenticated ADMIN can view detailed health status components")
    void testAdminHealthEndpoint_showsComponents() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").exists())
                .andExpect(jsonPath("$.components.db").exists());
    }

    @Test
    @DisplayName("Unauthenticated access to /actuator/metrics is rejected with 401 Unauthorized")
    void testUnauthenticatedMetrics_isProtected() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-ADMIN (DRIVER) access to /actuator/metrics is rejected with 403 Forbidden")
    void testDriverMetrics_isForbidden() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated ADMIN can access /actuator/metrics successfully")
    void testAdminMetrics_isSuccess() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItem("smartpark.bookings.created")))
                .andExpect(jsonPath("$.names", hasItem("smartpark.bookings.conflicts")))
                .andExpect(jsonPath("$.names", hasItem("smartpark.rate_limit.rejected")))
                .andExpect(jsonPath("$.names", hasItem("smartpark.bookings.creation.time")));
    }

    @Test
    @DisplayName("Request without X-Correlation-ID receives a newly generated UUID in response header")
    void testCorrelationId_generatedWhenAbsent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .andReturn();

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(correlationId).isNotNull().isNotBlank();
        // Standard UUID format
        assertThat(correlationId).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    @DisplayName("Client-supplied valid X-Correlation-ID is echoed back in response header")
    void testCorrelationId_echoedWhenValid() throws Exception {
        String clientCorrelationId = "test-corr-id-123456";

        mockMvc.perform(get("/api/health")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, clientCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, clientCorrelationId));
    }

    @Test
    @DisplayName("Client-supplied oversized or invalid correlation ID is safely replaced with fresh UUID")
    void testCorrelationId_sanitizedWhenInvalid() throws Exception {
        // Exceeds 64 characters limit or contains illegal characters
        String invalidCorrelationId = "bad_corr_id_with_newline\n_and_malicious_script_<script>_and_exceeding_max_allowed_length_of_sixty_four_characters_xxxxxxxxxxxxxxxxxxxx";

        MvcResult result = mockMvc.perform(get("/api/health")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, invalidCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .andReturn();

        String echoedId = result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(echoedId).isNotEqualTo(invalidCorrelationId);
        assertThat(echoedId).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    @DisplayName("MDC is cleanly wiped after request completion to prevent context leakage")
    void testMdc_cleanedAfterRequest() throws Exception {
        mockMvc.perform(get("/api/health")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "test-mdc-cleanup"))
                .andExpect(status().isOk());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Booking creation increments smartpark.bookings.created counter and records latency in timer")
    void testBookingCreation_incrementsCreatedCounterAndTimer() throws Exception {
        double initialCount = metrics.getRegistry().get("smartpark.bookings.created").counter().count();
        long initialTimerCount = metrics.getRegistry().get("smartpark.bookings.creation.time").timer().count();

        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        CreateBookingRequest request = new CreateBookingRequest(
                driverVehicle.getId(),
                slot.getId(),
                start,
                end
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER));

        double afterCount = metrics.getRegistry().get("smartpark.bookings.created").counter().count();
        long afterTimerCount = metrics.getRegistry().get("smartpark.bookings.creation.time").timer().count();

        assertThat(afterCount).isEqualTo(initialCount + 1.0);
        assertThat(afterTimerCount).isEqualTo(initialTimerCount + 1L);
    }

    @Test
    @DisplayName("Booking conflict increments smartpark.bookings.conflicts counter")
    void testBookingConflict_incrementsConflictsCounter() throws Exception {
        Instant start = testDay.atTime(14, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(16, 0).atZone(istZone).toInstant();

        // First booking succeeds
        CreateBookingRequest request1 = new CreateBookingRequest(
                driverVehicle.getId(),
                slot.getId(),
                start,
                end
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        double initialConflicts = metrics.getRegistry().get("smartpark.bookings.conflicts").counter().count();

        // Second overlapping booking conflicts
        CreateBookingRequest request2 = new CreateBookingRequest(
                driverVehicle.getId(),
                slot.getId(),
                start.plusSeconds(1800),
                end.plusSeconds(1800)
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("BOOKING_CONFLICT")));

        double afterConflicts = metrics.getRegistry().get("smartpark.bookings.conflicts").counter().count();
        assertThat(afterConflicts).isEqualTo(initialConflicts + 1.0);
    }

    @Test
    @DisplayName("Rate limit rejection increments smartpark.rate_limit.rejected counter")
    void testRateLimitRejection_incrementsRejectedCounter() {
        double initial = metrics.getRegistry().get("smartpark.rate_limit.rejected").counter().count();
        metrics.incrementRateLimitRejected();
        double after = metrics.getRegistry().get("smartpark.rate_limit.rejected").counter().count();

        assertThat(after).isEqualTo(initial + 1.0);
    }

    @Test
    @DisplayName("Redis health indicator reports status UP without failing whole app")
    void testRedisHealthIndicator() {
        Health health = redisHealthIndicator.health();
        assertThat(health).isNotNull();
        // Since Redis is non-critical fail-open, health status is UP (either AVAILABLE or DEGRADED)
        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsKey("status");
    }

    @Test
    @DisplayName("Kafka health indicator reports status UP without failing whole app")
    void testKafkaHealthIndicator() {
        Health health = kafkaHealthIndicator.health();
        assertThat(health).isNotNull();
        // Since Kafka is non-critical outbox-buffered, health status is UP (either AVAILABLE or DEGRADED)
        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsKey("status");
    }
}
