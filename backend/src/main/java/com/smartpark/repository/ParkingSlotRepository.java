package com.smartpark.repository;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.SlotStatus;
import com.smartpark.entity.SlotType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ParkingSlot s WHERE s.id = :id")
    Optional<ParkingSlot> findByIdWithLock(@Param("id") Long id);

    List<ParkingSlot> findByParkingFloorId(Long parkingFloorId);
    List<ParkingSlot> findByParkingFloorIdOrderBySlotNumberAsc(Long parkingFloorId);
    List<ParkingSlot> findByParkingFloorIdAndStatus(Long parkingFloorId, SlotStatus status);
    List<ParkingSlot> findByParkingFloorIdAndSlotType(Long parkingFloorId, SlotType slotType);
    Optional<ParkingSlot> findByParkingFloorIdAndSlotNumber(Long parkingFloorId, String slotNumber);
    boolean existsByParkingFloorIdAndSlotNumber(Long parkingFloorId, String slotNumber);
    long countByParkingFloorId(Long parkingFloorId);
}

