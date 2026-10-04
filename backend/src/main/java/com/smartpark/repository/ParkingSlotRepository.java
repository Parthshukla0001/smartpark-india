package com.smartpark.repository;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.SlotStatus;
import com.smartpark.entity.SlotType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    List<ParkingSlot> findByParkingFloorId(Long parkingFloorId);
    List<ParkingSlot> findByParkingFloorIdAndStatus(Long parkingFloorId, SlotStatus status);
    List<ParkingSlot> findByParkingFloorIdAndSlotType(Long parkingFloorId, SlotType slotType);
    Optional<ParkingSlot> findByParkingFloorIdAndSlotNumber(Long parkingFloorId, String slotNumber);
}
