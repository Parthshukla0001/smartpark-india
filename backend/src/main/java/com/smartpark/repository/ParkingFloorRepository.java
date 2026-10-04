package com.smartpark.repository;

import com.smartpark.entity.ParkingFloor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingFloorRepository extends JpaRepository<ParkingFloor, Long> {
    List<ParkingFloor> findByParkingLotId(Long parkingLotId);
    Optional<ParkingFloor> findByParkingLotIdAndFloorNumber(Long parkingLotId, int floorNumber);
}
