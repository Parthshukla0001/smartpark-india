package com.smartpark.kafka.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Dedicated event schema for asynchronous Kafka event streaming.
 * Excludes sensitive personal data, passwords, and entity internals while
 * providing a unique eventId for idempotent consumer processing.
 */
public class BookingKafkaEvent {

    private UUID eventId;
    private String eventType;
    private Long bookingId;
    private Long userId;
    private Long parkingLotId;
    private Long floorId;
    private Long slotId;
    private String slotNumber;
    private Instant startTime;
    private Instant endTime;
    private Instant timestamp;
    private int version;

    public BookingKafkaEvent() {
    }

    public BookingKafkaEvent(
            UUID eventId,
            String eventType,
            Long bookingId,
            Long userId,
            Long parkingLotId,
            Long floorId,
            Long slotId,
            String slotNumber,
            Instant startTime,
            Instant endTime,
            Instant timestamp,
            int version
    ) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID();
        this.eventType = eventType;
        this.bookingId = bookingId;
        this.userId = userId;
        this.parkingLotId = parkingLotId;
        this.floorId = floorId;
        this.slotId = slotId;
        this.slotNumber = slotNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.version = version > 0 ? version : 1;
    }

    public static BookingKafkaEvent of(
            String eventType,
            Long bookingId,
            Long userId,
            Long parkingLotId,
            Long floorId,
            Long slotId,
            String slotNumber,
            Instant startTime,
            Instant endTime
    ) {
        return new BookingKafkaEvent(
                UUID.randomUUID(),
                eventType,
                bookingId,
                userId,
                parkingLotId,
                floorId,
                slotId,
                slotNumber,
                startTime,
                endTime,
                Instant.now(),
                1
        );
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
