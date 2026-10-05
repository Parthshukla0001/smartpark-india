package com.smartpark.repository;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM Booking b
        WHERE (b.status = :confirmedStatus AND b.endTime <= :now)
           OR (b.status = :pendingStatus AND (b.startTime <= :now OR b.endTime <= :now))
        ORDER BY b.endTime ASC
    """)
    List<Booking> findEligibleForExpiryWithLock(
            @Param("now") Instant now,
            @Param("confirmedStatus") BookingStatus confirmedStatus,
            @Param("pendingStatus") BookingStatus pendingStatus
    );

    List<Booking> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot.parkingFloor.parkingLot"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot.parkingFloor.parkingLot"})
    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot.parkingFloor.parkingLot"})
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot.parkingFloor.parkingLot"})
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    List<Booking> findByParkingSlotId(Long parkingSlotId);

    List<Booking> findByVehicleId(Long vehicleId);

    List<Booking> findByUserIdAndStatus(Long userId, BookingStatus status);

    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.parkingSlot.id = :slotId
          AND b.startTime < :requestedEndTime
          AND b.endTime > :requestedStartTime
          AND b.status IN :activeStatuses
    """)
    boolean existsByParkingSlotAndTimeOverlap(
            @Param("slotId") Long slotId,
            @Param("requestedStartTime") Instant requestedStartTime,
            @Param("requestedEndTime") Instant requestedEndTime,
            @Param("activeStatuses") Collection<BookingStatus> activeStatuses
    );
}

