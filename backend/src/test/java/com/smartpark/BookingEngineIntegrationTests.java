package com.smartpark;

import com.smartpark.entity.*;
import com.smartpark.repository.*;
import com.smartpark.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingEngineIntegrationTests {

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

    private User driver1;
    private User driver2;
    private String driver1Token;
    private String driver2Token;

    private Vehicle driver1Vehicle;
    private Vehicle driver2Vehicle;

    private ParkingLot parkingLot;
    private ParkingFloor parkingFloor;
    private ParkingSlot parkingSlot;

    private ZoneId istZone = ZoneId.of("Asia/Kolkata");
    private LocalDate testDay;

    @BeforeEach
    void setUp() {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();

        driver1 = userRepository.saveAndFlush(new User("Vikram", "Driver", "driver1.booking@smartpark.in",
                "+919811111111", passwordEncoder.encode("Password123"), driverRole));
        driver2 = userRepository.saveAndFlush(new User("Ananya", "Driver", "driver2.booking@smartpark.in",
                "+919822222222", passwordEncoder.encode("Password123"), driverRole));

        driver1Token = jwtService.generateToken(driver1.getId(), driver1.getEmail(), RoleType.DRIVER.name());
        driver2Token = jwtService.generateToken(driver2.getId(), driver2.getEmail(), RoleType.DRIVER.name());

        driver1Vehicle = vehicleRepository.saveAndFlush(new Vehicle(driver1, "OD02AB1234", VehicleType.CAR));
        driver2Vehicle = vehicleRepository.saveAndFlush(new Vehicle(driver2, "OD02CD5678", VehicleType.SUV));

        parkingLot = new ParkingLot("Bhubaneswar Central Park", "Janpath", "Bhubaneswar", "Odisha",
                "751001", new BigDecimal("20.2961000"), new BigDecimal("85.8245000"));
        parkingLot.setOpeningTime(LocalTime.of(6, 0));
        parkingLot.setClosingTime(LocalTime.of(23, 0));
        parkingLot.setActive(true);
        parkingLot = parkingLotRepository.saveAndFlush(parkingLot);

        parkingFloor = parkingFloorRepository.saveAndFlush(new ParkingFloor(parkingLot, 1, "Basement 1"));
        parkingSlot = parkingSlotRepository.saveAndFlush(new ParkingSlot(parkingFloor, "B1-01", SlotType.REGULAR, SlotStatus.AVAILABLE));

        testDay = LocalDate.now(istZone).plusDays(2);
    }

    // =========================================================================
    // 1. AUTHORIZATION & OWNERSHIP TESTS
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated user cannot create a booking (401 Unauthorized)")
    void testUnauthenticatedCannotBook() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(11, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Driver can create a booking for their own vehicle")
    void testDriverCanCreateBooking() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.userId", is(driver1.getId().intValue())))
                .andExpect(jsonPath("$.vehicleRegistrationNumber", is("OD02AB1234")))
                .andExpect(jsonPath("$.slotNumber", is("B1-01")))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    @DisplayName("Driver cannot use another user's vehicle (404 Not Found)")
    void testDriverCannotUseOtherUserVehicle() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        // Driver 1 attempts to book using Driver 2's vehicle
        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Vehicle not found")));
    }

    @Test
    @DisplayName("Driver cannot access another user's booking by ID (404 Not Found)")
    void testDriverCannotAccessOtherUserBooking() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking bookingDriver2 = bookingRepository.saveAndFlush(
                new Booking(driver2, driver2Vehicle, parkingSlot, start, end, BookingStatus.CONFIRMED));

        // Driver 1 attempts to view Driver 2's booking
        mockMvc.perform(get("/api/bookings/" + bookingDriver2.getId())
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Booking not found")));
    }

    @Test
    @DisplayName("Driver cannot cancel another user's booking (404 Not Found)")
    void testDriverCannotCancelOtherUserBooking() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking bookingDriver2 = bookingRepository.saveAndFlush(
                new Booking(driver2, driver2Vehicle, parkingSlot, start, end, BookingStatus.CONFIRMED));

        // Driver 1 attempts to cancel Driver 2's booking
        mockMvc.perform(patch("/api/bookings/" + bookingDriver2.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Booking not found")));

        // Status remains CONFIRMED
        Booking refreshed = bookingRepository.findById(bookingDriver2.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // =========================================================================
    // 2. VALIDATION RULES
    // =========================================================================

    @Test
    @DisplayName("Reject booking with inactive vehicle (400 Bad Request)")
    void testRejectInactiveVehicle() throws Exception {
        driver1Vehicle.setActive(false);
        vehicleRepository.saveAndFlush(driver1Vehicle);

        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive vehicle")));
    }

    @Test
    @DisplayName("Reject booking on OUT_OF_SERVICE slot (400 Bad Request)")
    void testRejectOutOfServiceSlot() throws Exception {
        parkingSlot.setStatus(SlotStatus.OUT_OF_SERVICE);
        parkingSlotRepository.saveAndFlush(parkingSlot);

        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("out of service")));
    }

    @Test
    @DisplayName("Reject booking on inactive parking lot (400 Bad Request)")
    void testRejectInactiveParkingLot() throws Exception {
        parkingLot.setActive(false);
        parkingLotRepository.saveAndFlush(parkingLot);

        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive parking facility")));
    }

    @Test
    @DisplayName("Reject booking when end time is before or equal to start time (400 Bad Request)")
    void testRejectEndTimeBeforeStartTime() throws Exception {
        Instant start = testDay.atTime(12, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(10, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("end time must be after start time")));
    }

    @Test
    @DisplayName("Reject booking starting in the past (400 Bad Request)")
    void testRejectPastBooking() throws Exception {
        Instant pastStart = Instant.now().minus(Duration.ofHours(2));
        Instant pastEnd = pastStart.plus(Duration.ofHours(1));

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), pastStart, pastEnd);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be in the past")));
    }

    @Test
    @DisplayName("Reject duration less than 15 minutes (400 Bad Request)")
    void testRejectDurationTooShort() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = start.plus(Duration.ofMinutes(10)); // 10 minutes

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least 15 minutes")));
    }

    @Test
    @DisplayName("Reject duration exceeding 24 hours (400 Bad Request)")
    void testRejectDurationTooLong() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = start.plus(Duration.ofHours(25)); // 25 hours

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot exceed 24 hours")));
    }

    @Test
    @DisplayName("Reject booking outside parking lot operating hours (400 Bad Request)")
    void testRejectOutsideOperatingHours() throws Exception {
        // Operating hours are 06:00 to 23:00. Attempt 04:00 to 05:30.
        Instant start = testDay.atTime(4, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(5, 30).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver1Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("outside parking lot operating hours")));
    }

    // =========================================================================
    // 3. AVAILABILITY & OVERLAP DETECTION
    // =========================================================================

    @Test
    @DisplayName("Interval overlap: Existing [10:00 - 12:00], requested [11:00 - 13:00] conflicts (409)")
    void testOverlapPartialRight() throws Exception {
        Instant baseStart = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant baseEnd = testDay.atTime(12, 0).atZone(istZone).toInstant();
        bookingRepository.saveAndFlush(new Booking(driver1, driver1Vehicle, parkingSlot, baseStart, baseEnd, BookingStatus.CONFIRMED));

        Instant conflictStart = testDay.atTime(11, 0).atZone(istZone).toInstant();
        Instant conflictEnd = testDay.atTime(13, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), conflictStart, conflictEnd);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("BOOKING_CONFLICT")))
                .andExpect(jsonPath("$.message", containsString("not available")));
    }

    @Test
    @DisplayName("Interval overlap: Existing [10:00 - 12:00], requested [09:00 - 11:00] conflicts (409)")
    void testOverlapPartialLeft() throws Exception {
        Instant baseStart = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant baseEnd = testDay.atTime(12, 0).atZone(istZone).toInstant();
        bookingRepository.saveAndFlush(new Booking(driver1, driver1Vehicle, parkingSlot, baseStart, baseEnd, BookingStatus.CONFIRMED));

        Instant conflictStart = testDay.atTime(9, 0).atZone(istZone).toInstant();
        Instant conflictEnd = testDay.atTime(11, 0).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), conflictStart, conflictEnd);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("BOOKING_CONFLICT")));
    }

    @Test
    @DisplayName("Interval overlap: Existing [10:00 - 12:00], requested [10:30 - 11:30] contained conflicts (409)")
    void testOverlapContained() throws Exception {
        Instant baseStart = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant baseEnd = testDay.atTime(12, 0).atZone(istZone).toInstant();
        bookingRepository.saveAndFlush(new Booking(driver1, driver1Vehicle, parkingSlot, baseStart, baseEnd, BookingStatus.CONFIRMED));

        Instant conflictStart = testDay.atTime(10, 30).atZone(istZone).toInstant();
        Instant conflictEnd = testDay.atTime(11, 30).atZone(istZone).toInstant();

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), conflictStart, conflictEnd);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("BOOKING_CONFLICT")));
    }

    @Test
    @DisplayName("Interval overlap: Existing [10:00 - 12:00], requested exact [10:00 - 12:00] conflicts (409)")
    void testOverlapExactMatch() throws Exception {
        Instant baseStart = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant baseEnd = testDay.atTime(12, 0).atZone(istZone).toInstant();
        bookingRepository.saveAndFlush(new Booking(driver1, driver1Vehicle, parkingSlot, baseStart, baseEnd, BookingStatus.CONFIRMED));

        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), baseStart, baseEnd);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("BOOKING_CONFLICT")));
    }

    @Test
    @DisplayName("Adjacent intervals do NOT conflict: [08:00 - 10:00] and [12:00 - 14:00] succeed against [10:00 - 12:00]")
    void testAdjacentIntervalsSucceed() throws Exception {
        Instant baseStart = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant baseEnd = testDay.atTime(12, 0).atZone(istZone).toInstant();
        bookingRepository.saveAndFlush(new Booking(driver1, driver1Vehicle, parkingSlot, baseStart, baseEnd, BookingStatus.CONFIRMED));

        // 1. Preceding adjacent booking ending exactly at 10:00
        Instant adjBeforeStart = testDay.atTime(8, 0).atZone(istZone).toInstant();
        Instant adjBeforeEnd = testDay.atTime(10, 0).atZone(istZone).toInstant();

        String jsonBefore = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), adjBeforeStart, adjBeforeEnd);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBefore))
                .andExpect(status().isCreated());

        // 2. Succeeding adjacent booking starting exactly at 12:00
        Instant adjAfterStart = testDay.atTime(12, 0).atZone(istZone).toInstant();
        Instant adjAfterEnd = testDay.atTime(14, 0).atZone(istZone).toInstant();

        String jsonAfter = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), adjAfterStart, adjAfterEnd);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonAfter))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Cancelled booking frees the slot: [10:00 - 12:00] can be rebooked")
    void testCancelledBookingFreesSlot() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        // Initial booking by Driver 1
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, parkingSlot, start, end, BookingStatus.CONFIRMED));

        // Driver 1 cancels booking
        mockMvc.perform(patch("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        // Now Driver 2 can book the exact same time slot without conflict!
        String json = String.format("""
            {
                "vehicleId": %d,
                "parkingSlotId": %d,
                "startTime": "%s",
                "endTime": "%s"
            }
            """, driver2Vehicle.getId(), parkingSlot.getId(), start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    // =========================================================================
    // 4. LIFECYCLE & CANCELLATION RULES
    // =========================================================================

    @Test
    @DisplayName("Cannot cancel already cancelled booking (400 Bad Request)")
    void testCannotCancelAlreadyCancelledBooking() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        Booking booking = bookingRepository.saveAndFlush(
                new Booking(driver1, driver1Vehicle, parkingSlot, start, end, BookingStatus.CANCELLED));

        mockMvc.perform(patch("/api/bookings/" + booking.getId() + "/cancel")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already cancelled")));
    }

    // =========================================================================
    // 5. QUERY & PAGINATION
    // =========================================================================

    @Test
    @DisplayName("Driver gets only their own bookings with pagination")
    void testGetMyBookingsPagination() throws Exception {
        Instant start = testDay.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = testDay.atTime(12, 0).atZone(istZone).toInstant();

        bookingRepository.saveAndFlush(new Booking(driver1, driver1Vehicle, parkingSlot, start, end, BookingStatus.CONFIRMED));
        bookingRepository.saveAndFlush(new Booking(driver2, driver2Vehicle, parkingSlot, start.plus(Duration.ofDays(1)), end.plus(Duration.ofDays(1)), BookingStatus.CONFIRMED));

        mockMvc.perform(get("/api/bookings")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId", is(driver1.getId().intValue())))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }
}
