package com.smartpark.websocket.listener;

import com.smartpark.websocket.event.ParkingDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transaction-aware listener that dispatches parking updates to STOMP WebSocket topics.
 * Crucially guarantees that events are only emitted after the underlying database
 * transaction has successfully committed, preventing ghost notifications on rolled-back transactions.
 */
@Component
public class ParkingEventListener {

    private static final Logger log = LoggerFactory.getLogger(ParkingEventListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public ParkingEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleParkingEvent(ParkingDomainEvent event) {
        String destination = "/topic/parking-lots/" + event.getParkingLotId();
        log.info("Dispatched WebSocket event {} to {} (slotId={})",
                event.getPayload().getEventType(), destination, event.getPayload().getSlotId());
        try {
            messagingTemplate.convertAndSend(destination, event.getPayload());
        } catch (Exception ex) {
            log.error("Failed to broadcast WebSocket event to {}: {}", destination, ex.getMessage(), ex);
        }
    }
}
