package com.smartpark;

import com.smartpark.dto.booking.BookingResponse;
import com.smartpark.dto.booking.CreateBookingRequest;
import com.smartpark.dto.common.PageResponse;
import com.smartpark.dto.parking.ParkingLotResponse;
import com.smartpark.entity.*;
import com.smartpark.exception.BookingConflictException;
import com.smartpark.repository.*;
import com.smartpark.security.UserPrincipal;
import com.smartpark.service.BookingService;
import com.smartpark.service.ParkingLotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class QueryOptimizationIntegrationTests {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingLotService parkingLotService;

    @Autowired
    private ParkingFloorRepository parkingFloorRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.smartpark.kafka.outbox.OutboxPublisher outboxPublisher;

    private User testDriver;
    private Vehicle testVehicle;
    private ParkingLot testLot;
    private ParkingFloor testFloor;
    private ParkingSlot testSlot;
    private final ZoneId istZone = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        if (outboxPublisher != null) {
            outboxPublisher.setOutboxEnabled(false);
        }
        cleanDatabase();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Role driverRole = roleRepository.findByName(RoleType.DRIVER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.DRIVER, "Driver")));

        testDriver = new User(
                "Optimization",
                "Tester",
                "perf." + suffix + "@smartpark.in",
                "+91987" + (int) (1000000 + Math.random() * 8999999),
                passwordEncoder.encode("Password123!"),
                driverRole
        );
        testDriver = userRepository.saveAndFlush(testDriver);

        testVehicle = new Vehicle(testDriver, "OD-02-" + suffix.toUpperCase(), VehicleType.CAR);
        testVehicle = vehicleRepository.saveAndFlush(testVehicle);

        testLot = new ParkingLot(
                "City Terminal " + suffix,
                "Janpath Road",
                "Bhubaneswar",
                "Odisha",
                "751001",
                new BigDecimal("20.2961"),
                new BigDecimal("85.8245")
        );
        testLot.setOpeningTime(LocalTime.of(0, 0));
        testLot.setClosingTime(LocalTime.of(23, 59));
        testLot = parkingLotRepository.saveAndFlush(testLot);

        testFloor = new ParkingFloor(testLot, 1, "Level 1");
        testFloor = parkingFloorRepository.saveAndFlush(testFloor);

        testSlot = new ParkingSlot(testFloor, "P-" + suffix, SlotType.REGULAR, SlotStatus.AVAILABLE);
        testSlot = parkingSlotRepository.saveAndFlush(testSlot);

        authenticateAs(testDriver);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        if (outboxPublisher != null) {
            outboxPublisher.setOutboxEnabled(true);
        }
    }

    private void cleanDatabase() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
        outboxEventRepository.deleteAll();
        bookingRepository.deleteAll();
        vehicleRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingFloorRepository.deleteAll();
        parkingLotRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.fromUser(user);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("1. EntityGraph optimization: getMyBookings populates full response graph without N+1 queries")
    void testGetMyBookingsEntityGraphOptimization() {
        LocalDate tomorrow = LocalDate.now(istZone).plusDays(1);

        for (int i = 0; i < 5; i++) {
            Instant start = tomorrow.atTime(8 + (i * 2), 0).atZone(istZone).toInstant();
            Instant end = tomorrow.atTime(9 + (i * 2), 0).atZone(istZone).toInstant();

            Booking booking = new Booking(testDriver, testVehicle, testSlot, start, end,
                    i % 2 == 0 ? BookingStatus.CONFIRMED : BookingStatus.PENDING);
            bookingRepository.save(booking);
        }
        bookingRepository.flush();

        PageResponse<BookingResponse> page = bookingService.getMyBookings(null, 0, 10);

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(1);

        // Verify that eagerly joined graph properties are populated cleanly
        for (BookingResponse item : page.getContent()) {
            assertThat(item.getUserEmail()).isEqualTo(testDriver.getEmail());
            assertThat(item.getVehicleRegistrationNumber()).isEqualTo(testVehicle.getRegistrationNumber());
            assertThat(item.getSlotNumber()).isEqualTo(testSlot.getSlotNumber());
            assertThat(item.getFloorName()).isEqualTo(testFloor.getName());
            assertThat(item.getParkingLotName()).isEqualTo(testLot.getName());
        }
    }

    @Test
    @DisplayName("2. Sorting correctness: getMyBookings returns results sorted by startTime DESC")
    void testGetMyBookingsSortingCorrectness() {
        LocalDate day = LocalDate.now(istZone).plusDays(2);

        Instant t1 = day.atTime(8, 0).atZone(istZone).toInstant();
        Instant t2 = day.atTime(11, 0).atZone(istZone).toInstant();
        Instant t3 = day.atTime(15, 0).atZone(istZone).toInstant();

        bookingRepository.save(new Booking(testDriver, testVehicle, testSlot, t1, t1.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED));
        bookingRepository.save(new Booking(testDriver, testVehicle, testSlot, t3, t3.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED));
        bookingRepository.save(new Booking(testDriver, testVehicle, testSlot, t2, t2.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED));
        bookingRepository.flush();

        PageResponse<BookingResponse> response = bookingService.getMyBookings(null, 0, 10);
        List<BookingResponse> items = response.getContent();
        assertThat(items).hasSize(3);

        // Descending start time check
        assertThat(items.get(0).getStartTime()).isEqualTo(t3);
        assertThat(items.get(1).getStartTime()).isEqualTo(t2);
        assertThat(items.get(2).getStartTime()).isEqualTo(t1);
    }

    @Test
    @DisplayName("3. Filtering correctness: getMyBookings filtered by status returns only matching bookings")
    void testGetMyBookingsFilteringByStatus() {
        LocalDate day = LocalDate.now(istZone).plusDays(3);

        Instant t1 = day.atTime(9, 0).atZone(istZone).toInstant();
        Instant t2 = day.atTime(12, 0).atZone(istZone).toInstant();

        bookingRepository.save(new Booking(testDriver, testVehicle, testSlot, t1, t1.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED));
        bookingRepository.save(new Booking(testDriver, testVehicle, testSlot, t2, t2.plus(1, ChronoUnit.HOURS), BookingStatus.CANCELLED));
        bookingRepository.flush();

        PageResponse<BookingResponse> confirmedOnly = bookingService.getMyBookings(BookingStatus.CONFIRMED, 0, 10);
        assertThat(confirmedOnly.getContent()).hasSize(1);
        assertThat(confirmedOnly.getContent().get(0).getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        PageResponse<BookingResponse> cancelledOnly = bookingService.getMyBookings(BookingStatus.CANCELLED, 0, 10);
        assertThat(cancelledOnly.getContent()).hasSize(1);
        assertThat(cancelledOnly.getContent().get(0).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("4. Case-insensitive functional index filtering: getParkingLots matches regardless of casing")
    void testParkingLotCaseInsensitiveFiltering() {
        // Create second lot in another city
        ParkingLot mumbaiLot = new ParkingLot(
                "BKC Terminal",
                "Bandra Kurla",
                "Mumbai",
                "Maharashtra",
                "400051",
                new BigDecimal("19.0657"),
                new BigDecimal("72.8687")
        );
        mumbaiLot.setActive(true);
        parkingLotRepository.saveAndFlush(mumbaiLot);

        // Query with uppercase/lowercase variations
        PageResponse<ParkingLotResponse> upperCaseQuery = parkingLotService.getParkingLots(
                "BHUBANESWAR", null, null, true, 0, 10, "name", "asc"
        );
        assertThat(upperCaseQuery.getContent()).hasSize(1);
        assertThat(upperCaseQuery.getContent().get(0).getCity()).isEqualTo("Bhubaneswar");

        PageResponse<ParkingLotResponse> lowerCaseQuery = parkingLotService.getParkingLots(
                "bhubaneswar", null, null, true, 0, 10, "name", "asc"
        );
        assertThat(lowerCaseQuery.getContent()).hasSize(1);
        assertThat(lowerCaseQuery.getContent().get(0).getCity()).isEqualTo("Bhubaneswar");

        PageResponse<ParkingLotResponse> mumbaiQuery = parkingLotService.getParkingLots(
                "mumbai", null, null, true, 0, 10, "name", "asc"
        );
        assertThat(mumbaiQuery.getContent()).hasSize(1);
        assertThat(mumbaiQuery.getContent().get(0).getCity()).isEqualTo("Mumbai");
    }

    @Test
    @DisplayName("5. Booking overlap availability query correctness with partial index")
    void testBookingOverlapAvailabilityCorrectness() {
        LocalDate day = LocalDate.now(istZone).plusDays(4);

        Instant reservedStart = day.atTime(10, 0).atZone(istZone).toInstant();
        Instant reservedEnd = day.atTime(12, 0).atZone(istZone).toInstant();

        // 1. Confirmed booking in slot
        bookingRepository.saveAndFlush(new Booking(testDriver, testVehicle, testSlot, reservedStart, reservedEnd, BookingStatus.CONFIRMED));

        // Overlapping request [11:00, 13:00) should be detected as overlap
        boolean overlapDetected = bookingRepository.existsByParkingSlotAndTimeOverlap(
                testSlot.getId(),
                day.atTime(11, 0).atZone(istZone).toInstant(),
                day.atTime(13, 0).atZone(istZone).toInstant(),
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)
        );
        assertThat(overlapDetected).isTrue();

        // Adjacent request [12:00, 14:00) touching boundary should NOT overlap
        boolean adjacentDetected = bookingRepository.existsByParkingSlotAndTimeOverlap(
                testSlot.getId(),
                day.atTime(12, 0).atZone(istZone).toInstant(),
                day.atTime(14, 0).atZone(istZone).toInstant(),
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)
        );
        assertThat(adjacentDetected).isFalse();
    }

    @Test
    @DisplayName("6. Phase 5 GiST exclusion constraint remains authoritative and prevents concurrent double booking")
    void testGiSTExclusionConstraintRemainsAuthoritative() {
        LocalDate day = LocalDate.now(istZone).plusDays(5);
        Instant start = day.atTime(14, 0).atZone(istZone).toInstant();
        Instant end = day.atTime(16, 0).atZone(istZone).toInstant();

        CreateBookingRequest request = new CreateBookingRequest(testVehicle.getId(), testSlot.getId(), start, end);
        var firstBooking = bookingService.createBooking(request);
        assertThat(firstBooking).isNotNull();

        // Attempting to book the identical slot and time window must trigger conflict
        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("7. NotificationRepository pagination and sorting correctness")
    void testNotificationPaginationAndSorting() {
        for (int i = 0; i < 5; i++) {
            Notification notification = new Notification(
                    testDriver,
                    UUID.randomUUID(),
                    "BOOKING_CREATED",
                    "Notification message " + i
            );
            notificationRepository.save(notification);
        }
        notificationRepository.flush();

        Page<Notification> firstPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                testDriver.getId(), PageRequest.of(0, 3)
        );

        assertThat(firstPage.getContent()).hasSize(3);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        // Verify descending order of created_at
        List<Notification> items = firstPage.getContent();
        for (int i = 0; i < items.size() - 1; i++) {
            assertThat(items.get(i).getCreatedAt()).isAfterOrEqualTo(items.get(i + 1).getCreatedAt());
        }
    }
}
