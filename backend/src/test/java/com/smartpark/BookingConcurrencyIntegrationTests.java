package com.smartpark;

import com.smartpark.entity.*;
import com.smartpark.repository.*;
import com.smartpark.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingConcurrencyIntegrationTests {

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

    private User driverA;
    private User driverB;
    private String tokenA;
    private String tokenB;

    private Vehicle vehicleA;
    private Vehicle vehicleB;

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

        driverA = userRepository.saveAndFlush(new User("Ramesh", "Driver", "ramesh.driver@smartpark.in",
                "+919876511111", passwordEncoder.encode("Password123"), driverRole));
        driverB = userRepository.saveAndFlush(new User("Suresh", "Driver", "suresh.driver@smartpark.in",
                "+919876522222", passwordEncoder.encode("Password123"), driverRole));

        tokenA = jwtService.generateToken(driverA.getId(), driverA.getEmail(), RoleType.DRIVER.name());
        tokenB = jwtService.generateToken(driverB.getId(), driverB.getEmail(), RoleType.DRIVER.name());

        vehicleA = vehicleRepository.saveAndFlush(new Vehicle(driverA, "OD02CONC01", VehicleType.CAR));
        vehicleB = vehicleRepository.saveAndFlush(new Vehicle(driverB, "OD02CONC02", VehicleType.SUV));

        parkingLot = new ParkingLot("Tech Hub Multi-Level Parking", "Infocity Square", "Bhubaneswar", "Odisha",
                "751024", new BigDecimal("20.3540000"), new BigDecimal("85.8180000"));
        parkingLot.setOpeningTime(LocalTime.of(6, 0));
        parkingLot.setClosingTime(LocalTime.of(23, 0));
        parkingLot.setActive(true);
        parkingLot = parkingLotRepository.saveAndFlush(parkingLot);

        parkingFloor = parkingFloorRepository.saveAndFlush(new ParkingFloor(parkingLot, 1, "Level 1"));
        slot1 = parkingSlotRepository.saveAndFlush(new ParkingSlot(parkingFloor, "L1-01", SlotType.REGULAR, SlotStatus.AVAILABLE));
        slot2 = parkingSlotRepository.saveAndFlush(new ParkingSlot(parkingFloor, "L1-02", SlotType.REGULAR, SlotStatus.AVAILABLE));

        testDay = LocalDate.now(istZone).plusDays(3);
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

    private record ConcurrencyResult(int successCount, int conflictCount, List<Integer> statusCodes) {}

    private ConcurrencyResult executeConcurrentBookings(
            String token1, Long vehicle1Id, Long slot1Id, Instant start1, Instant end1,
            String token2, Long vehicle2Id, Long slot2Id, Instant start2, Instant end2
    ) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        Runnable task1 = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                String json = String.format("""
                    {
                        "vehicleId": %d,
                        "parkingSlotId": %d,
                        "startTime": "%s",
                        "endTime": "%s"
                    }
                    """, vehicle1Id, slot1Id, start1, end1);

                MvcResult result = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + token1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andReturn();

                int status = result.getResponse().getStatus();
                statusCodes.add(status);
                if (status == 201) successCount.incrementAndGet();
                if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        Runnable task2 = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                String json = String.format("""
                    {
                        "vehicleId": %d,
                        "parkingSlotId": %d,
                        "startTime": "%s",
                        "endTime": "%s"
                    }
                    """, vehicle2Id, slot2Id, start2, end2);

                MvcResult result = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + token2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andReturn();

                int status = result.getResponse().getStatus();
                statusCodes.add(status);
                if (status == 201) successCount.incrementAndGet();
                if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
        assertThat(ready).isTrue();

        startLatch.countDown(); // Release both threads concurrently

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        return new ConcurrencyResult(successCount.get(), conflictCount.get(), statusCodes);
    }

    // =========================================================================
    // 1. CONCURRENT OVERLAPPING RESERVATIONS (EXACTLY ONE MUST SUCCEED)
    // =========================================================================

    @Test
    @DisplayName("Concurrency Pattern A: Exact same interval [10:00 - 11:00] -> Exactly 1 succeeds, 1 receives 409 Conflict")
    void testConcurrentExactIntervalDoubleBookingPrevention() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(11, 0).atZone(istZone).toInstant();

        ConcurrencyResult result = executeConcurrentBookings(
                tokenA, vehicleA.getId(), slot1.getId(), start, end,
                tokenB, vehicleB.getId(), slot1.getId(), start, end
        );

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.conflictCount()).isEqualTo(1);
        assertThat(result.statusCodes()).containsExactlyInAnyOrder(201, 409);

        // Database verification: Only 1 active booking must exist for slot1
        List<Booking> activeBookings = bookingRepository.findByParkingSlotId(slot1.getId());
        assertThat(activeBookings).hasSize(1);
        assertThat(activeBookings.get(0).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Concurrency Pattern B: Partial overlap [10:00 - 12:00] vs [11:00 - 13:00] -> Exactly 1 succeeds, 1 receives 409 Conflict")
    void testConcurrentPartialOverlapDoubleBookingPrevention() throws Exception {
        Instant start1 = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end1 = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Instant start2 = testDay.atTime(11, 0).atZone(istZone).toInstant();
        Instant end2 = testDay.atTime(13, 0).atZone(istZone).toInstant();

        ConcurrencyResult result = executeConcurrentBookings(
                tokenA, vehicleA.getId(), slot1.getId(), start1, end1,
                tokenB, vehicleB.getId(), slot1.getId(), start2, end2
        );

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.conflictCount()).isEqualTo(1);
        assertThat(result.statusCodes()).containsExactlyInAnyOrder(201, 409);

        List<Booking> activeBookings = bookingRepository.findByParkingSlotId(slot1.getId());
        assertThat(activeBookings).hasSize(1);
    }

    @Test
    @DisplayName("Concurrency Pattern C: Contained interval [10:00 - 14:00] vs [11:00 - 12:00] -> Exactly 1 succeeds, 1 receives 409 Conflict")
    void testConcurrentContainedIntervalDoubleBookingPrevention() throws Exception {
        Instant start1 = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end1 = testDay.atTime(14, 0).atZone(istZone).toInstant();

        Instant start2 = testDay.atTime(11, 0).atZone(istZone).toInstant();
        Instant end2 = testDay.atTime(12, 0).atZone(istZone).toInstant();

        ConcurrencyResult result = executeConcurrentBookings(
                tokenA, vehicleA.getId(), slot1.getId(), start1, end1,
                tokenB, vehicleB.getId(), slot1.getId(), start2, end2
        );

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.conflictCount()).isEqualTo(1);
        assertThat(result.statusCodes()).containsExactlyInAnyOrder(201, 409);

        List<Booking> activeBookings = bookingRepository.findByParkingSlotId(slot1.getId());
        assertThat(activeBookings).hasSize(1);
    }

    // =========================================================================
    // 2. CONCURRENT NON-CONFLICTING RESERVATIONS (BOTH MUST SUCCEED)
    // =========================================================================

    @Test
    @DisplayName("Concurrency Pattern D: Adjacent intervals [10:00 - 11:00) and [11:00 - 12:00) -> BOTH succeed without conflict")
    void testConcurrentAdjacentIntervalsBothSucceed() throws Exception {
        Instant start1 = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end1 = testDay.atTime(11, 0).atZone(istZone).toInstant();

        Instant start2 = testDay.atTime(11, 0).atZone(istZone).toInstant();
        Instant end2 = testDay.atTime(12, 0).atZone(istZone).toInstant();

        ConcurrencyResult result = executeConcurrentBookings(
                tokenA, vehicleA.getId(), slot1.getId(), start1, end1,
                tokenB, vehicleB.getId(), slot1.getId(), start2, end2
        );

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.conflictCount()).isEqualTo(0);
        assertThat(result.statusCodes()).containsExactly(201, 201);

        List<Booking> activeBookings = bookingRepository.findByParkingSlotId(slot1.getId());
        assertThat(activeBookings).hasSize(2);
    }

    @Test
    @DisplayName("Concurrency Pattern E: Different slots (Slot 1 vs Slot 2) at exact same time [10:00 - 11:00] -> BOTH succeed")
    void testConcurrentDifferentSlotsBothSucceed() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(11, 0).atZone(istZone).toInstant();

        ConcurrencyResult result = executeConcurrentBookings(
                tokenA, vehicleA.getId(), slot1.getId(), start, end,
                tokenB, vehicleB.getId(), slot2.getId(), start, end
        );

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.conflictCount()).isEqualTo(0);
        assertThat(result.statusCodes()).containsExactly(201, 201);

        assertThat(bookingRepository.findByParkingSlotId(slot1.getId())).hasSize(1);
        assertThat(bookingRepository.findByParkingSlotId(slot2.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Concurrency Pattern F: Same slot with disjoint time windows [10:00 - 11:00] and [14:00 - 15:00] -> BOTH succeed")
    void testConcurrentDisjointTimeWindowsBothSucceed() throws Exception {
        Instant start1 = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end1 = testDay.atTime(11, 0).atZone(istZone).toInstant();

        Instant start2 = testDay.atTime(14, 0).atZone(istZone).toInstant();
        Instant end2 = testDay.atTime(15, 0).atZone(istZone).toInstant();

        ConcurrencyResult result = executeConcurrentBookings(
                tokenA, vehicleA.getId(), slot1.getId(), start1, end1,
                tokenB, vehicleB.getId(), slot1.getId(), start2, end2
        );

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.conflictCount()).isEqualTo(0);
        assertThat(result.statusCodes()).containsExactly(201, 201);

        assertThat(bookingRepository.findByParkingSlotId(slot1.getId())).hasSize(2);
    }

    // =========================================================================
    // 3. CANCELLATION RELEASE UNDER EXCLUSION CONSTRAINT
    // =========================================================================

    @Test
    @DisplayName("Exclusion constraint respects status: CANCELLED booking releases the slot for immediate rebooking")
    void testCancelledBookingReleasesSlotUnderExclusionConstraint() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        // 1. Driver A creates initial booking
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driverA, vehicleA, slot1, start, end, BookingStatus.CONFIRMED));

        // 2. Driver A cancels booking
        mockMvc.perform(patch("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        // 3. Driver B books the exact same interval; the exclusion constraint must NOT block it!
        String jsonB = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, vehicleB.getId(), slot1.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        // Verify database: 1 CANCELLED, 1 CONFIRMED
        List<Booking> allBookings = bookingRepository.findByParkingSlotId(slot1.getId());
        assertThat(allBookings).hasSize(2);
        assertThat(allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count()).isEqualTo(1);
        assertThat(allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count()).isEqualTo(1);
    }
}
