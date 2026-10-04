package com.smartpark.repository;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByParkingSlotId(Long parkingSlotId);
    List<Booking> findByVehicleId(Long vehicleId);
    List<Booking> findByUserIdAndStatus(Long userId, BookingStatus status);
}
