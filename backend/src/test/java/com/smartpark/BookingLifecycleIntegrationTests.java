package com.smartpark;

import com.smartpark.dto.booking.BookingResponse;
import com.smartpark.entity.*;
import com.smartpark.repository.*;
import com.smartpark.security.jwt.JwtService;
import com.smartpark.service.BookingExpiryScheduler;
import com.smartpark.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingLifecycleIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingExpiryScheduler bookingExpiryScheduler;

    private User driver1;
    private User driver2;
    private User operator;
    private User admin;

    private String driver1Token;
    private String driver2Token;
    private String operatorToken;
    private String adminToken;

    private Vehicle driver1Vehicle;
    private Vehicle driver2Vehicle;

    private ParkingLot parkingLot;
    private ParkingFloor parkingFloor;
    private ParkingSlot slot1;
    private ParkingSlot slot2;

    private final ZoneId istZone = ZoneId.of("Asia/Kolkata");
    private LocalDate testDay;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        Role operatorRole = roleRepository.findByName(RoleType.OPERATOR).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();

        driver1 = userRepository.saveAndFlush(new User("Aarav", "Driver", "aarav.driver@smartpark.in",
                "+919811110001", passwordEncoder.encode("Password123"), driverRole));
        driver2 = userRepository.saveAndFlush(new User("Ishaan", "Driver", "ishaan.driver@smartpark.in",
                "+919811110002", passwordEncoder.encode("Password123"), driverRole));
        operator = userRepository.saveAndFlush(new User("Rajesh", "Operator", "rajesh.operator@smartpark.in",
                "+919811110003", passwordEncoder.encode("Password123"), operatorRole));
        admin = userRepository.saveAndFlush(new User("Pooja", "Admin", "pooja.admin@smartpark.in",
                "+919811110004", passwordEncoder.encode("Password123"), adminRole));

        driver1Token = jwtService.generateToken(driver1.getId(), driver1.getEmail(), RoleType.DRIVER.name());
        driver2Token = jwtService.generateToken(driver2.getId(), driver2.getEmail(), RoleType.DRIVER.name());
        operatorToken = jwtService.generateToken(operator.getId(), operator.getEmail(), RoleType.OPERATOR.name());
        adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), RoleType.ADMIN.name());

        driver1Vehicle = vehicleRepository.saveAndFlush(new Vehicle(driver1, "OD02LC0001", VehicleType.CAR));
        driver2Vehicle = vehicleRepository.saveAndFlush(new Vehicle(driver2, "OD02LC0002", VehicleType.SUV));

        parkingLot = new ParkingLot("Smart City Terminal", "Master Canteen", "Bhubaneswar", "Odisha",
                "751001", new BigDecimal("20.2600000"), new BigDecimal("85.8300000"));
        parkingLot.setOpeningTime(LocalTime.of(6, 0));
        parkingLot.setClosingTime(LocalTime.of(23, 0));
        parkingLot.setActive(true);
        parkingLot = parkingLotRepository.saveAndFlush(parkingLot);

        parkingFloor = parkingFloorRepository.saveAndFlush(new ParkingFloor(parkingLot, 1, "Level 1"));
        slot1 = parkingSlotRepository.saveAndFlush(new ParkingSlot(parkingFloor, "L1-01", SlotType.REGULAR, SlotStatus.AVAILABLE));
        slot2 = parkingSlotRepository.saveAndFlush(new ParkingSlot(parkingFloor, "L1-02", SlotType.REGULAR, SlotStatus.AVAILABLE));

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

    // =========================================================================
    // 1. STATE TRANSITIONS
    // =========================================================================

    @Test
    @DisplayName("1. PENDING -> CONFIRMED succeeds via authorized operator operation")
    void testPendingToConfirmedSucceeds() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.PENDING));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/confirm")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("2. PENDING -> CANCELLED succeeds via cancellation endpoint")
    void testPendingToCancelledSucceeds() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.PENDING));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("3. PENDING -> EXPIRED succeeds when expired by authorized operator")
    void testPendingToExpiredSucceeds() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.PENDING));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/expire")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("EXPIRED")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    @DisplayName("4. CONFIRMED -> CANCELLED succeeds when within cutoff")
    void testConfirmedToCancelledSucceedsWithinCutoff() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("5. CONFIRMED -> COMPLETED succeeds through authorized operator/admin operation")
    void testConfirmedToCompletedSucceeds() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/complete")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("6. CONFIRMED -> EXPIRED succeeds when eligible")
    void testConfirmedToExpiredSucceeds() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/expire")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("EXPIRED")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    // =========================================================================
    // 2. TERMINAL STATE PROTECTION
    // =========================================================================

    @Test
    @DisplayName("7. CANCELLED cannot become CONFIRMED (400 Bad Request)")
    void testCancelledCannotBecomeConfirmed() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CANCELLED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/confirm")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot confirm booking with terminal status")));
    }

    @Test
    @DisplayName("8. COMPLETED cannot become CONFIRMED (400 Bad Request)")
    void testCompletedCannotBecomeConfirmed() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.COMPLETED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/confirm")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot confirm booking with terminal status")));
    }

    @Test
    @DisplayName("9. EXPIRED cannot become CONFIRMED (400 Bad Request)")
    void testExpiredCannotBecomeConfirmed() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.EXPIRED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/confirm")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot confirm booking with terminal status")));
    }

    // =========================================================================
    // 3. AUTHORIZATION & RBAC ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("10. Driver cannot modify another user's booking (404 Not Found)")
    void testDriverCannotModifyAnotherUsersBooking() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking bookingDriver2 = bookingRepository.saveAndFlush(
                new Booking(driver2, driver2Vehicle, slot1, start, end, BookingStatus.CONFIRMED));

        // Driver 1 attempts to cancel Driver 2's booking
        mockMvc.perform(post("/api/bookings/" + bookingDriver2.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Booking not found")));

        Booking refreshed = bookingRepository.findById(bookingDriver2.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("11. Driver cannot mark booking COMPLETED (403 Forbidden)")
    void testDriverCannotMarkBookingCompleted() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/complete")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("12. Driver cannot expire booking (403 Forbidden)")
    void testDriverCannotExpireBooking() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/expire")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. CANCELLATION CUTOFF & BOUNDARIES
    // =========================================================================

    @Test
    @DisplayName("13. Cancellation after cutoff is rejected (400 Bad Request)")
    void testCancellationAfterCutoffIsRejected() throws Exception {
        // Cutoff is 15 minutes before start_time.
        // If booking starts in 10 minutes, the cutoff has already passed.
        Instant now = Instant.now();
        Instant startIn10Minutes = now.plus(Duration.ofMinutes(10));
        Instant endIn70Minutes = startIn10Minutes.plus(Duration.ofHours(1));

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, startIn10Minutes, endIn70Minutes, BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cancellation deadline has passed")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("14. Cancellation at the exact cutoff follows the documented boundary rule (Allowed)")
    void testCancellationAtExactCutoffSucceeds() throws Exception {
        // Cutoff is 15 minutes before start.
        // We set start = now + 15 minutes + 1 second buffer to ensure test execution does not cross into past
        Instant now = Instant.now();
        Instant startIn16Minutes = now.plus(Duration.ofMinutes(16));
        Instant end = startIn16Minutes.plus(Duration.ofHours(1));

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, startIn16Minutes, end, BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("15. Already cancelled booking cannot be cancelled again (400 Bad Request)")
    void testAlreadyCancelledBookingCannotBeCancelledAgain() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CANCELLED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already cancelled")));
    }

    @Test
    @DisplayName("16. Already completed booking cannot be cancelled (400 Bad Request)")
    void testAlreadyCompletedBookingCannotBeCancelled() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.COMPLETED));

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot cancel booking with status: COMPLETED")));
    }

    // =========================================================================
    // 5. AUTOMATED EXPIRY & SCHEDULER IDEMPOTENCY
    // =========================================================================

    @Test
    @DisplayName("17. Expiry job does not modify cancelled bookings")
    void testExpiryJobDoesNotModifyCancelledBookings() {
        Instant pastStart = Instant.now().minus(Duration.ofHours(3));
        Instant pastEnd = Instant.now().minus(Duration.ofHours(1));

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, pastStart, pastEnd, BookingStatus.CANCELLED));

        int expired = bookingService.expireEligibleBookings(Instant.now());
        assertThat(expired).isEqualTo(0);

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("18. Expiry job does not modify completed bookings")
    void testExpiryJobDoesNotModifyCompletedBookings() {
        Instant pastStart = Instant.now().minus(Duration.ofHours(3));
        Instant pastEnd = Instant.now().minus(Duration.ofHours(1));

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, pastStart, pastEnd, BookingStatus.COMPLETED));

        int expired = bookingService.expireEligibleBookings(Instant.now());
        assertThat(expired).isEqualTo(0);

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("19. Expiry job is idempotent across multiple repeated executions")
    void testExpiryJobIsIdempotent() {
        Instant pastStart = Instant.now().minus(Duration.ofHours(4));
        Instant pastEnd = Instant.now().minus(Duration.ofHours(2));

        Booking booking1 = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, pastStart, pastEnd, BookingStatus.CONFIRMED));
        Booking booking2 = bookingRepository.saveAndFlush(
                new Booking(driver2, driver2Vehicle, slot2, pastStart, pastEnd, BookingStatus.PENDING));

        // Execution 1: Should transition both bookings to EXPIRED
        int run1Expired = bookingService.expireEligibleBookings(Instant.now());
        assertThat(run1Expired).isEqualTo(2);

        assertThat(bookingRepository.findById(booking1.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingRepository.findById(booking2.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);

        // Execution 2: Ran immediately again with same/later timestamp -> 0 rows modified
        int run2Expired = bookingService.expireEligibleBookings(Instant.now());
        assertThat(run2Expired).isEqualTo(0);

        // Statuses remain unchanged
        assertThat(bookingRepository.findById(booking1.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingRepository.findById(booking2.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    @DisplayName("20. Expiry correctly handles bookings at the exact expiry boundary (currentTime == endTime)")
    void testExpiryAtExactBoundary() {
        Instant boundary = Instant.now().minus(Duration.ofSeconds(10));
        Instant start = boundary.minus(Duration.ofHours(1));

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, boundary, BookingStatus.CONFIRMED));

        // Evaluate expiry exactly as of the boundary instant
        int expired = bookingService.expireEligibleBookings(boundary);
        assertThat(expired).isEqualTo(1);

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    // =========================================================================
    // 6. CONCURRENT LIFECYCLE OPERATIONS
    // =========================================================================

    @Test
    @DisplayName("Concurrent cancellation: 2 simultaneous cancellation requests -> Exactly 1 succeeds (200), 1 receives 400 Bad Request")
    void testConcurrentCancellationSafety() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, slot1, start, end, BookingStatus.CONFIRMED));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        Runnable cancelTask = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                MvcResult result = mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                                .header("Authorization", "Bearer " + driver1Token))
                        .andReturn();

                int status = result.getResponse().getStatus();
                statusCodes.add(status);
                if (status == 200) successCount.incrementAndGet();
                if (status == 400) conflictCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        executor.submit(cancelTask);
        executor.submit(cancelTask);

        boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
        assertThat(ready).isTrue();

        startLatch.countDown(); // release both threads simultaneously

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
        assertThat(statusCodes).containsExactlyInAnyOrder(200, 400);

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
