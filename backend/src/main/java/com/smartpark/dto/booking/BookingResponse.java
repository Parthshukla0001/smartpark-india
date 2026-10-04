package com.smartpark.dto.booking;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.SlotType;
import com.smartpark.entity.VehicleType;

import java.time.Instant;

public class BookingResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private Long vehicleId;
    private String vehicleRegistrationNumber;
    private VehicleType vehicleType;
    private Long parkingSlotId;
    private String slotNumber;
    private SlotType slotType;
    private Long parkingFloorId;
    private String floorName;
    private Long parkingLotId;
    private String parkingLotName;
    private Instant startTime;
    private Instant endTime;
    private BookingStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public BookingResponse() {
    }

    public BookingResponse(Long id, Long userId, String userEmail, Long vehicleId, String vehicleRegistrationNumber,
                           VehicleType vehicleType, Long parkingSlotId, String slotNumber, SlotType slotType,
                           Long parkingFloorId, String floorName, Long parkingLotId, String parkingLotName,
                           Instant startTime, Instant endTime, BookingStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.vehicleId = vehicleId;
        this.vehicleRegistrationNumber = vehicleRegistrationNumber;
        this.vehicleType = vehicleType;
        this.parkingSlotId = parkingSlotId;
        this.slotNumber = slotNumber;
        this.slotType = slotType;
        this.parkingFloorId = parkingFloorId;
        this.floorName = floorName;
        this.parkingLotId = parkingLotId;
        this.parkingLotName = parkingLotName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static BookingResponse fromEntity(Booking booking) {
        if (booking == null) return null;

        Long userId = booking.getUser() != null ? booking.getUser().getId() : null;
        String userEmail = booking.getUser() != null ? booking.getUser().getEmail() : null;

        Long vehicleId = booking.getVehicle() != null ? booking.getVehicle().getId() : null;
        String vehicleReg = booking.getVehicle() != null ? booking.getVehicle().getRegistrationNumber() : null;
        VehicleType vType = booking.getVehicle() != null ? booking.getVehicle().getVehicleType() : null;

        Long slotId = booking.getParkingSlot() != null ? booking.getParkingSlot().getId() : null;
        String slotNum = booking.getParkingSlot() != null ? booking.getParkingSlot().getSlotNumber() : null;
        SlotType sType = booking.getParkingSlot() != null ? booking.getParkingSlot().getSlotType() : null;

        Long floorId = (booking.getParkingSlot() != null && booking.getParkingSlot().getParkingFloor() != null)
                ? booking.getParkingSlot().getParkingFloor().getId() : null;
        String floorName = (booking.getParkingSlot() != null && booking.getParkingSlot().getParkingFloor() != null)
                ? booking.getParkingSlot().getParkingFloor().getName() : null;

        Long lotId = (booking.getParkingSlot() != null && booking.getParkingSlot().getParkingFloor() != null &&
                booking.getParkingSlot().getParkingFloor().getParkingLot() != null)
                ? booking.getParkingSlot().getParkingFloor().getParkingLot().getId() : null;
        String lotName = (booking.getParkingSlot() != null && booking.getParkingSlot().getParkingFloor() != null &&
                booking.getParkingSlot().getParkingFloor().getParkingLot() != null)
                ? booking.getParkingSlot().getParkingFloor().getParkingLot().getName() : null;

        return new BookingResponse(
                booking.getId(),
                userId,
                userEmail,
                vehicleId,
                vehicleReg,
                vType,
                slotId,
                slotNum,
                sType,
                floorId,
                floorName,
                lotId,
                lotName,
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getVehicleRegistrationNumber() {
        return vehicleRegistrationNumber;
    }

    public void setVehicleRegistrationNumber(String vehicleRegistrationNumber) {
        this.vehicleRegistrationNumber = vehicleRegistrationNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Long getParkingSlotId() {
        return parkingSlotId;
    }

    public void setParkingSlotId(Long parkingSlotId) {
        this.parkingSlotId = parkingSlotId;
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

    public Long getParkingFloorId() {
        return parkingFloorId;
    }

    public void setParkingFloorId(Long parkingFloorId) {
        this.parkingFloorId = parkingFloorId;
    }

    public String getFloorName() {
        return floorName;
    }

    public void setFloorName(String floorName) {
        this.floorName = floorName;
    }

    public Long getParkingLotId() {
        return parkingLotId;
    }

    public void setParkingLotId(Long parkingLotId) {
        this.parkingLotId = parkingLotId;
    }

    public String getParkingLotName() {
        return parkingLotName;
    }

    public void setParkingLotName(String parkingLotName) {
        this.parkingLotName = parkingLotName;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
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
