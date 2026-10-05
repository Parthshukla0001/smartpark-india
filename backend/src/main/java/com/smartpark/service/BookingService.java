package com.smartpark.service;

import com.smartpark.dto.booking.BookingResponse;
import com.smartpark.dto.booking.CreateBookingRequest;
import com.smartpark.dto.common.PageResponse;
import com.smartpark.entity.*;
import com.smartpark.exception.BookingConflictException;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.kafka.event.BookingKafkaEvent;
import com.smartpark.kafka.outbox.OutboxService;
import com.smartpark.observability.SmartParkMetrics;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.VehicleRepository;
import com.smartpark.security.service.CurrentUserService;
import com.smartpark.security.service.SecurityAuditLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final int cancellationCutoffMinutes;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final SmartParkMetrics metrics;

    @Autowired
    public BookingService(BookingRepository bookingRepository,
                          VehicleRepository vehicleRepository,
                          ParkingSlotRepository parkingSlotRepository,
                          CurrentUserService currentUserService,
                          SecurityAuditLogger auditLogger,
                          Clock clock,
                          @Value("${smartpark.booking.cancellation-cutoff-minutes:15}") int cancellationCutoffMinutes,
                          org.springframework.context.ApplicationEventPublisher eventPublisher,
                          @Autowired(required = false) OutboxService outboxService,
                          @Autowired(required = false) SmartParkMetrics metrics) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.parkingSlotRepository = parkingSlotRepository;
        this.currentUserService = currentUserService;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.cancellationCutoffMinutes = cancellationCutoffMinutes;
        this.eventPublisher = eventPublisher;
        this.outboxService = outboxService;
        this.metrics = metrics;
    }

    public BookingService(BookingRepository bookingRepository,
                          VehicleRepository vehicleRepository,
                          ParkingSlotRepository parkingSlotRepository,
                          CurrentUserService currentUserService,
                          SecurityAuditLogger auditLogger,
                          Clock clock) {
        this(bookingRepository, vehicleRepository, parkingSlotRepository, currentUserService, auditLogger, clock, 15, null, null, null);
    }

    private void publishParkingEvent(Long lotId, com.smartpark.websocket.dto.ParkingEventPayload payload) {
        if (eventPublisher != null && lotId != null) {
            eventPublisher.publishEvent(new com.smartpark.websocket.event.ParkingDomainEvent(lotId, payload));
        }
    }

    private void saveOutboxEvent(String eventType, Booking booking) {
        if (outboxService == null || booking == null) {
            return;
        }
        ParkingSlot slot = booking.getParkingSlot();
        ParkingFloor floor = slot != null ? slot.getParkingFloor() : null;
        ParkingLot lot = floor != null ? floor.getParkingLot() : null;

        BookingKafkaEvent event = BookingKafkaEvent.of(
                eventType,
                booking.getId(),
                booking.getUser() != null ? booking.getUser().getId() : null,
                lot != null ? lot.getId() : null,
                floor != null ? floor.getId() : null,
                slot != null ? slot.getId() : null,
                slot != null ? slot.getSlotNumber() : null,
                booking.getStartTime(),
                booking.getEndTime()
        );
        outboxService.saveEvent("BOOKING", booking.getId(), eventType, event);
    }


    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        long startNanos = System.nanoTime();
        try {
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

            // 2. Validate parking slot exists and is bookable (acquire pessimistic write lock on slot row to serialize concurrent reservations on the same slot)
            ParkingSlot slot = parkingSlotRepository.findByIdWithLock(request.getParkingSlotId())
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
                if (metrics != null) {
                    metrics.incrementBookingsConflicts();
                }
                auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_REJECTED, currentUser.getEmail(),
                        "Slot id=" + slot.getId() + " is already booked for the requested time");
                throw new BookingConflictException("Parking slot is not available for the requested time");
            }

            // 7. Initial status is server-controlled and starts as CONFIRMED
            Booking booking = new Booking(currentUser, vehicle, slot, startTime, endTime, BookingStatus.CONFIRMED);
            Booking savedBooking;
            try {
                savedBooking = bookingRepository.saveAndFlush(booking);
            } catch (DataIntegrityViolationException ex) {
                String rootMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
                if (rootMessage != null && rootMessage.contains("uq_bookings_no_overlapping_active_slots")) {
                    if (metrics != null) {
                        metrics.incrementBookingsConflicts();
                    }
                    auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_REJECTED, currentUser.getEmail(),
                            "Concurrent race conflict: slot id=" + slot.getId() + " was booked simultaneously");
                    throw new BookingConflictException("Parking slot is not available for the requested time");
                }
                throw ex;
            }

            if (metrics != null) {
                metrics.incrementBookingsCreated();
            }

            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_CREATED, currentUser.getEmail(),
                    "Created booking id=" + savedBooking.getId() + " for slot id=" + slot.getId() +
                            " vehicle id=" + vehicle.getId());

            publishParkingEvent(lot.getId(),
                    com.smartpark.websocket.dto.ParkingEventPayload.forBookingEvent(
                            com.smartpark.websocket.ParkingEventType.BOOKING_CREATED,
                            lot.getId(),
                            floor.getId(),
                            slot.getId(),
                            slot.getSlotNumber(),
                            slot.getSlotType(),
                            slot.getStatus(),
                            savedBooking.getId(),
                            savedBooking.getStatus(),
                            savedBooking.getStartTime(),
                            savedBooking.getEndTime()
                    ));

            saveOutboxEvent("BOOKING_CREATED", savedBooking);

            return BookingResponse.fromEntity(savedBooking);
        } finally {
            if (metrics != null) {
                metrics.recordBookingCreationTime(System.nanoTime() - startNanos);
            }
        }
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

    public PageResponse<BookingResponse> getAllBookings(BookingStatus status, int page, int size) {
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
            bookingPage = bookingRepository.findByStatus(status, pageable);
        } else {
            bookingPage = bookingRepository.findAllBy(pageable);
        }

        Page<BookingResponse> responsePage = bookingPage.map(BookingResponse::fromEntity);
        return PageResponse.from(responsePage);
    }

    public BookingResponse getBookingById(Long id) {
        User currentUser = currentUserService.getCurrentUserEntity();
        boolean isDriver = currentUser.getRole().getName() == RoleType.DRIVER;

        Booking booking;
        if (isDriver) {
            booking = bookingRepository.findByIdAndUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        } else {
            booking = bookingRepository.findByIdWithDetails(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        }

        return BookingResponse.fromEntity(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long id) {
        User currentUser = currentUserService.getCurrentUserEntity();
        boolean isDriver = currentUser.getRole().getName() == RoleType.DRIVER;

        // 1. Acquire row-level lock to prevent concurrent race conditions
        Booking booking = bookingRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        // 2. Ownership check: Driver can only cancel their own booking (return 404 to avoid ID enumeration)
        if (isDriver && !booking.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Booking not found with id: " + id);
        }

        // 3. Strict lifecycle validation: terminal states cannot be cancelled
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot cancel booking with status: COMPLETED");
        }
        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new IllegalArgumentException("Cannot cancel booking with status: EXPIRED");
        }

        // Only CONFIRMED or PENDING bookings may be cancelled
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Cannot cancel booking with status: " + booking.getStatus());
        }

        // 4. Cancellation cutoff validation for DRIVER
        if (isDriver) {
            Instant cutoffDeadline = booking.getStartTime().minus(Duration.ofMinutes(cancellationCutoffMinutes));
            Instant now = Instant.now(clock);
            if (now.isAfter(cutoffDeadline)) {
                auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_REJECTED, currentUser.getEmail(),
                        "Cancellation rejected: cutoff deadline passed for booking id=" + id);
                throw new IllegalArgumentException("Cancellation deadline has passed. Bookings must be cancelled at least " +
                        cancellationCutoffMinutes + " minutes before start time");
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking updatedBooking = bookingRepository.saveAndFlush(booking);

        if (metrics != null) {
            metrics.incrementBookingsCancelled();
        }

        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_CANCELLED, currentUser.getEmail(),
                "Cancelled booking id=" + id);

        ParkingSlot slot = updatedBooking.getParkingSlot();
        ParkingFloor floor = slot != null ? slot.getParkingFloor() : null;
        ParkingLot lot = floor != null ? floor.getParkingLot() : null;
        if (lot != null) {
            publishParkingEvent(lot.getId(),
                    com.smartpark.websocket.dto.ParkingEventPayload.forBookingEvent(
                            com.smartpark.websocket.ParkingEventType.BOOKING_CANCELLED,
                            lot.getId(),
                            floor.getId(),
                            slot.getId(),
                            slot.getSlotNumber(),
                            slot.getSlotType(),
                            slot.getStatus(),
                            updatedBooking.getId(),
                            updatedBooking.getStatus(),
                            updatedBooking.getStartTime(),
                            updatedBooking.getEndTime()
                    ));
        }

        saveOutboxEvent("BOOKING_CANCELLED", updatedBooking);

        return BookingResponse.fromEntity(updatedBooking);
    }

    @Transactional
    public BookingResponse confirmBooking(Long id) {
        User currentUser = currentUserService.getCurrentUserEntity();

        Booking booking = bookingRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        // Terminal states cannot become CONFIRMED
        if (booking.getStatus() == BookingStatus.CANCELLED ||
                booking.getStatus() == BookingStatus.COMPLETED ||
                booking.getStatus() == BookingStatus.EXPIRED) {
            throw new IllegalArgumentException("Cannot confirm booking with terminal status: " + booking.getStatus());
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Cannot confirm booking with status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking updatedBooking = bookingRepository.saveAndFlush(booking);

        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_CONFIRMED, currentUser.getEmail(),
                "Confirmed booking id=" + id);

        ParkingSlot slot = updatedBooking.getParkingSlot();
        ParkingFloor floor = slot != null ? slot.getParkingFloor() : null;
        ParkingLot lot = floor != null ? floor.getParkingLot() : null;
        if (lot != null) {
            publishParkingEvent(lot.getId(),
                    com.smartpark.websocket.dto.ParkingEventPayload.forBookingEvent(
                            com.smartpark.websocket.ParkingEventType.BOOKING_CONFIRMED,
                            lot.getId(),
                            floor.getId(),
                            slot.getId(),
                            slot.getSlotNumber(),
                            slot.getSlotType(),
                            slot.getStatus(),
                            updatedBooking.getId(),
                            updatedBooking.getStatus(),
                            updatedBooking.getStartTime(),
                            updatedBooking.getEndTime()
                    ));
        }

        saveOutboxEvent("BOOKING_CONFIRMED", updatedBooking);

        return BookingResponse.fromEntity(updatedBooking);
    }

    @Transactional
    public BookingResponse completeBooking(Long id) {
        User currentUser = currentUserService.getCurrentUserEntity();

        Booking booking = bookingRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Cannot complete booking with status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.COMPLETED);
        Booking updatedBooking = bookingRepository.saveAndFlush(booking);

        if (metrics != null) {
            metrics.incrementBookingsCompleted();
        }

        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_COMPLETED, currentUser.getEmail(),
                "Completed booking id=" + id);

        ParkingSlot slot = updatedBooking.getParkingSlot();
        ParkingFloor floor = slot != null ? slot.getParkingFloor() : null;
        ParkingLot lot = floor != null ? floor.getParkingLot() : null;
        if (lot != null) {
            publishParkingEvent(lot.getId(),
                    com.smartpark.websocket.dto.ParkingEventPayload.forBookingEvent(
                            com.smartpark.websocket.ParkingEventType.BOOKING_COMPLETED,
                            lot.getId(),
                            floor.getId(),
                            slot.getId(),
                            slot.getSlotNumber(),
                            slot.getSlotType(),
                            slot.getStatus(),
                            updatedBooking.getId(),
                            updatedBooking.getStatus(),
                            updatedBooking.getStartTime(),
                            updatedBooking.getEndTime()
                    ));
        }

        saveOutboxEvent("BOOKING_COMPLETED", updatedBooking);

        return BookingResponse.fromEntity(updatedBooking);
    }

    @Transactional
    public BookingResponse expireBooking(Long id) {
        User currentUser = currentUserService.getCurrentUserEntity();

        Booking booking = bookingRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            return BookingResponse.fromEntity(booking);
        }

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot expire booking with status: " + booking.getStatus());
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Cannot expire booking with status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.EXPIRED);
        Booking updatedBooking = bookingRepository.saveAndFlush(booking);

        if (metrics != null) {
            metrics.incrementBookingsExpired();
        }

        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_EXPIRED, currentUser.getEmail(),
                "Expired booking id=" + id);

        ParkingSlot slot = updatedBooking.getParkingSlot();
        ParkingFloor floor = slot != null ? slot.getParkingFloor() : null;
        ParkingLot lot = floor != null ? floor.getParkingLot() : null;
        if (lot != null) {
            publishParkingEvent(lot.getId(),
                    com.smartpark.websocket.dto.ParkingEventPayload.forBookingEvent(
                            com.smartpark.websocket.ParkingEventType.BOOKING_EXPIRED,
                            lot.getId(),
                            floor.getId(),
                            slot.getId(),
                            slot.getSlotNumber(),
                            slot.getSlotType(),
                            slot.getStatus(),
                            updatedBooking.getId(),
                            updatedBooking.getStatus(),
                            updatedBooking.getStartTime(),
                            updatedBooking.getEndTime()
                    ));
        }

        saveOutboxEvent("BOOKING_EXPIRED", updatedBooking);

        return BookingResponse.fromEntity(updatedBooking);
    }

    @Transactional
    public int expireEligibleBookings(Instant asOfTime) {
        List<Booking> eligibleBookings = bookingRepository.findEligibleForExpiryWithLock(
                asOfTime, BookingStatus.CONFIRMED, BookingStatus.PENDING);

        if (eligibleBookings.isEmpty()) {
            return 0;
        }

        for (Booking booking : eligibleBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.BOOKING_EXPIRED, "SYSTEM_SCHEDULER",
                    "Auto-expired booking id=" + booking.getId());

            ParkingSlot slot = booking.getParkingSlot();
            ParkingFloor floor = slot != null ? slot.getParkingFloor() : null;
            ParkingLot lot = floor != null ? floor.getParkingLot() : null;
            if (lot != null) {
                publishParkingEvent(lot.getId(),
                        com.smartpark.websocket.dto.ParkingEventPayload.forBookingEvent(
                                com.smartpark.websocket.ParkingEventType.BOOKING_EXPIRED,
                                lot.getId(),
                                floor.getId(),
                                slot.getId(),
                                slot.getSlotNumber(),
                                slot.getSlotType(),
                                slot.getStatus(),
                                booking.getId(),
                                booking.getStatus(),
                                booking.getStartTime(),
                                booking.getEndTime()
                        ));
            }

            saveOutboxEvent("BOOKING_EXPIRED", booking);
        }

        bookingRepository.flush();

        if (metrics != null) {
            metrics.incrementBookingsExpired(eligibleBookings.size());
        }

        return eligibleBookings.size();
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
