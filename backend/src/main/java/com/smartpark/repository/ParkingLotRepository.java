package com.smartpark.repository;

import com.smartpark.entity.ParkingLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long>, JpaSpecificationExecutor<ParkingLot> {
    List<ParkingLot> findByCityIgnoreCase(String city);
    List<ParkingLot> findByActiveTrue();
    boolean existsByNameIgnoreCaseAndCityIgnoreCase(String name, String city);
}
