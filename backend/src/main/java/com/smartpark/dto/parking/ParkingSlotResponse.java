package com.smartpark.dto.parking;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.SlotStatus;
import com.smartpark.entity.SlotType;

import java.time.Instant;

public class ParkingSlotResponse {

    private Long id;
    private Long parkingFloorId;
    private String slotNumber;
    private SlotType slotType;
    private SlotStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public ParkingSlotResponse() {
    }

    public ParkingSlotResponse(Long id, Long parkingFloorId, String slotNumber, SlotType slotType, SlotStatus status,
                               Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.parkingFloorId = parkingFloorId;
        this.slotNumber = slotNumber;
        this.slotType = slotType;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ParkingSlotResponse fromEntity(ParkingSlot slot) {
        if (slot == null) return null;
        return new ParkingSlotResponse(
                slot.getId(),
                slot.getParkingFloor() != null ? slot.getParkingFloor().getId() : null,
                slot.getSlotNumber(),
                slot.getSlotType(),
                slot.getStatus(),
                slot.getCreatedAt(),
                slot.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParkingFloorId() {
        return parkingFloorId;
    }

    public void setParkingFloorId(Long parkingFloorId) {
        this.parkingFloorId = parkingFloorId;
    }

    public String getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(String slotNumber) {
        this.slotNumber = slotNumber;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
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
