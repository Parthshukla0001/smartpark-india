package com.smartpark.repository;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    Optional<Booking> findByIdAndUserId(Long id, Long userId);

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
