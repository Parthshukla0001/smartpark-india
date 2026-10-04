package com.smartpark.service;

import com.smartpark.dto.booking.BookingResponse;
import com.smartpark.dto.booking.CreateBookingRequest;
import com.smartpark.dto.common.PageResponse;
import com.smartpark.entity.*;
import com.smartpark.exception.BookingConflictException;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.VehicleRepository;
import com.smartpark.security.service.CurrentUserService;
import com.smartpark.security.service.SecurityAuditLogger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private static final Duration MIN_DURATION = Duration.ofMinutes(15);
    private static final Duration MAX_DURATION = Duration.ofHours(24);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<BookingStatus> ACTIVE_RESERVING_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.PENDING
    );

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final CurrentUserService currentUserService;
    private final SecurityAuditLogger auditLogger;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          VehicleRepository vehicleRepository,
                          ParkingSlotRepository parkingSlotRepository,
                          CurrentUserService currentUserService,
                          SecurityAuditLogger auditLogger,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.parkingSlotRepository = parkingSlotRepository;
        this.currentUserService = currentUserService;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        User currentUser = currentUserService.getCurrentUserEntity();

        // 1. Validate vehicle exists, belongs to authenticated user, and is active
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

        if (!vehicle.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId());
        }

        if (!vehicle.isActive()) {
            throw new IllegalArgumentException("Cannot create booking for an inactive vehicle");
        }

        // 2. Validate parking slot exists and is bookable
        ParkingSlot slot = parkingSlotRepository.findById(request.getParkingSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Parking slot not found with id: " + request.getParkingSlotId()));

        if (slot.getStatus() == SlotStatus.OUT_OF_SERVICE) {
            throw new IllegalArgumentException("Parking slot is out of service and cannot be booked");
        }

        // 3. Validate hierarchy and parent facility status
        ParkingFloor floor = slot.getParkingFloor();
        if (floor == null) {
            throw new ResourceNotFoundException("Associated parking floor not found");
        }
        ParkingLot lot = floor.getParkingLot();
        if (lot == null) {
            throw new ResourceNotFoundException("Associated parking lot not found");
        }
        if (!lot.isActive()) {
            throw new IllegalArgumentException("Cannot book parking slot in an inactive parking facility");
        }

        // 4. Validate time range and duration limits
        Instant startTime = request.getStartTime();
        Instant endTime = request.getEndTime();

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Booking end time must be after start time");
        }

        Instant now = Instant.now(clock);
        if (startTime.isBefore(now)) {
            throw new IllegalArgumentException("Booking start time cannot be in the past");
        }

        Duration duration = Duration.between(startTime, endTime);
        if (duration.compareTo(MIN_DURATION) < 0) {
            throw new IllegalArgumentException("Booking duration must be at least 15 minutes");
        }
        if (duration.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("Booking duration cannot exceed 24 hours");
        }

        // 5. Validate operating hours if configured on parking lot
        validateOperatingHours(lot, startTime, endTime);

        // 6. Application-level availability check for overlapping active reservations
        boolean hasOverlap = bookingRepository.existsByParkingSlotAndTimeOverlap(
                slot.getId(), startTime, endTime, ACTIVE_RESERVING_STATUSES);

        if (hasOverlap) {
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_REJECTED, currentUser.getEmail(),
                    "Slot id=" + slot.getId() + " is already booked for the requested time");
            throw new BookingConflictException("Parking slot is not available for the requested time");
        }

        // 7. Initial status is server-controlled and starts as CONFIRMED
        Booking booking = new Booking(currentUser, vehicle, slot, startTime, endTime, BookingStatus.CONFIRMED);
        Booking savedBooking = bookingRepository.save(booking);

        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_CREATED, currentUser.getEmail(),
                "Created booking id=" + savedBooking.getId() + " for slot id=" + slot.getId() +
                        " vehicle id=" + vehicle.getId());

        return BookingResponse.fromEntity(savedBooking);
    }

    public PageResponse<BookingResponse> getMyBookings(BookingStatus status, int page, int size) {
        User currentUser = currentUserService.getCurrentUserEntity();

        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        } else if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<Booking> bookingPage;

        if (status != null) {
            bookingPage = bookingRepository.findByUserIdAndStatus(currentUser.getId(), status, pageable);
        } else {
            bookingPage = bookingRepository.findByUserId(currentUser.getId(), pageable);
        }

        Page<BookingResponse> responsePage = bookingPage.map(BookingResponse::fromEntity);
        return PageResponse.from(responsePage);
    }

    public BookingResponse getBookingById(Long id) {
        User currentUser = currentUserService.getCurrentUserEntity();

        // Enforce ownership: query directly filters by booking ID and current user ID
        Booking booking = bookingRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        return BookingResponse.fromEntity(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long id) {
        User currentUser = currentUserService.getCurrentUserEntity();

        Booking booking = bookingRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        // Strict lifecycle transition: only CONFIRMED bookings may be cancelled
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new IllegalArgumentException("Cannot cancel booking with status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking updatedBooking = bookingRepository.save(booking);

        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_CANCELLED, currentUser.getEmail(),
                "Cancelled booking id=" + id);

        return BookingResponse.fromEntity(updatedBooking);
    }

    private void validateOperatingHours(ParkingLot lot, Instant startTime, Instant endTime) {
        if (lot.getOpeningTime() == null || lot.getClosingTime() == null) {
            return;
        }

        ZonedDateTime startZoned = startTime.atZone(FACILITY_ZONE);
        ZonedDateTime endZoned = endTime.atZone(FACILITY_ZONE);

        if (!startZoned.toLocalDate().equals(endZoned.toLocalDate())) {
            throw new IllegalArgumentException("Overnight bookings spanning multiple operating days are not supported. Bookings must be on the same day.");
        }

        LocalTime startLocal = startZoned.toLocalTime();
        LocalTime endLocal = endZoned.toLocalTime();

        if (startLocal.isBefore(lot.getOpeningTime()) || endLocal.isAfter(lot.getClosingTime())) {
            throw new IllegalArgumentException("Requested booking time (" + startLocal + " - " + endLocal +
                    ") falls outside parking lot operating hours (" + lot.getOpeningTime() + " - " + lot.getClosingTime() + ")");
        }
    }
}
