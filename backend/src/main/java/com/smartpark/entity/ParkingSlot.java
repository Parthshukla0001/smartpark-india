package com.smartpark.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "parking_slots", uniqueConstraints = {
    @UniqueConstraint(name = "uq_parking_slots_floor_slot", columnNames = {"parking_floor_id", "slot_number"})
})
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parking_floor_id", nullable = false)
    private ParkingFloor parkingFloor;

    @Column(name = "slot_number", nullable = false, length = 50)
    private String slotNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 50)
    private SlotType slotType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public ParkingSlot() {
    }

    public ParkingSlot(ParkingFloor parkingFloor, String slotNumber, SlotType slotType) {
        this.parkingFloor = parkingFloor;
        this.slotNumber = slotNumber;
        this.slotType = slotType;
        this.status = SlotStatus.AVAILABLE;
    }

    public ParkingSlot(ParkingFloor parkingFloor, String slotNumber, SlotType slotType, SlotStatus status) {
        this.parkingFloor = parkingFloor;
        this.slotNumber = slotNumber;
        this.slotType = slotType;
        this.status = status != null ? status : SlotStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ParkingFloor getParkingFloor() {
        return parkingFloor;
    }

    public void setParkingFloor(ParkingFloor parkingFloor) {
        this.parkingFloor = parkingFloor;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParkingSlot that = (ParkingSlot) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
