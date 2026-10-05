package com.smartpark.websocket.dto;

import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.SlotStatus;
import com.smartpark.entity.SlotType;
import com.smartpark.websocket.ParkingEventType;

import java.time.Instant;

/**
 * Data Transfer Object for real-time WebSocket events.
 * Encapsulates parking slot and booking updates without exposing sensitive
 * user data, credentials, or internal JPA entity internals.
 */
public class ParkingEventPayload {

    private ParkingEventType eventType;
    private Long parkingLotId;
    private Long floorId;
    private Long slotId;
    private String slotNumber;
    private SlotType slotType;
    private SlotStatus slotStatus;
    private Long bookingId;
    private BookingStatus bookingStatus;
    private Instant startTime;
    private Instant endTime;
    private Instant timestamp;

    public ParkingEventPayload() {
    }

    public ParkingEventPayload(
            ParkingEventType eventType,
            Long parkingLotId,
            Long floorId,
            Long slotId,
            String slotNumber,
            SlotType slotType,
            SlotStatus slotStatus,
            Long bookingId,
            BookingStatus bookingStatus,
            Instant startTime,
            Instant endTime,
            Instant timestamp
    ) {
        this.eventType = eventType;
        this.parkingLotId = parkingLotId;
        this.floorId = floorId;
        this.slotId = slotId;
        this.slotNumber = slotNumber;
        this.slotType = slotType;
        this.slotStatus = slotStatus;
        this.bookingId = bookingId;
        this.bookingStatus = bookingStatus;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static ParkingEventPayload forSlotStatus(
            Long parkingLotId,
            Long floorId,
            Long slotId,
            String slotNumber,
            SlotType slotType,
            SlotStatus slotStatus
    ) {
        return new ParkingEventPayload(
                ParkingEventType.SLOT_STATUS_CHANGED,
                parkingLotId,
                floorId,
                slotId,
                slotNumber,
                slotType,
                slotStatus,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static ParkingEventPayload forBookingEvent(
            ParkingEventType eventType,
            Long parkingLotId,
            Long floorId,
            Long slotId,
            String slotNumber,
            SlotType slotType,
            SlotStatus slotStatus,
            Long bookingId,
            BookingStatus bookingStatus,
            Instant startTime,
            Instant endTime
    ) {
        return new ParkingEventPayload(
                eventType,
                parkingLotId,
                floorId,
                slotId,
                slotNumber,
                slotType,
                slotStatus,
                bookingId,
                bookingStatus,
                startTime,
                endTime,
                Instant.now()
        );
    }

    public ParkingEventType getEventType() {
        return eventType;
    }

    public void setEventType(ParkingEventType eventType) {
        this.eventType = eventType;
    }

    public Long getParkingLotId() {
        return parkingLotId;
    }

    public void setParkingLotId(Long parkingLotId) {
        this.parkingLotId = parkingLotId;
    }

    public Long getFloorId() {
        return floorId;
    }

    public void setFloorId(Long floorId) {
        this.floorId = floorId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
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

    public SlotStatus getSlotStatus() {
        return slotStatus;
    }

    public void setSlotStatus(SlotStatus slotStatus) {
        this.slotStatus = slotStatus;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public BookingStatus getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(BookingStatus bookingStatus) {
        this.bookingStatus = bookingStatus;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
