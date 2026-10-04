package com.smartpark.dto.parking;

import com.smartpark.entity.ParkingFloor;

import java.time.Instant;

public class ParkingFloorResponse {

    private Long id;
    private Long parkingLotId;
    private int floorNumber;
    private String name;
    private Instant createdAt;
    private Instant updatedAt;

    public ParkingFloorResponse() {
    }

    public ParkingFloorResponse(Long id, Long parkingLotId, int floorNumber, String name, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.parkingLotId = parkingLotId;
        this.floorNumber = floorNumber;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ParkingFloorResponse fromEntity(ParkingFloor floor) {
        if (floor == null) return null;
        return new ParkingFloorResponse(
                floor.getId(),
                floor.getParkingLot() != null ? floor.getParkingLot().getId() : null,
                floor.getFloorNumber(),
                floor.getName(),
                floor.getCreatedAt(),
                floor.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParkingLotId() {
        return parkingLotId;
    }

    public void setParkingLotId(Long parkingLotId) {
        this.parkingLotId = parkingLotId;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(int floorNumber) {
        this.floorNumber = floorNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
