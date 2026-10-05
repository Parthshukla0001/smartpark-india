package com.smartpark.websocket.event;

import com.smartpark.websocket.dto.ParkingEventPayload;

/**
 * Spring application domain event representing a real-time parking change.
 * Broadcast across internal application layers and listened to by transaction-aware listeners.
 */
public class ParkingDomainEvent {

    private final Long parkingLotId;
    private final ParkingEventPayload payload;

    public ParkingDomainEvent(Long parkingLotId, ParkingEventPayload payload) {
        this.parkingLotId = parkingLotId;
        this.payload = payload;
    }

    public Long getParkingLotId() {
        return parkingLotId;
    }

    public ParkingEventPayload getPayload() {
        return payload;
    }
}
