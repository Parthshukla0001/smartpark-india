package com.smartpark.kafka.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.entity.OutboxEvent;
import com.smartpark.kafka.event.BookingKafkaEvent;
import com.smartpark.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for persisting domain events into the transactional outbox table.
 * All outbox records are inserted within the caller's active database transaction,
 * ensuring strict atomicity between state mutations and event registration.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes an event payload and inserts an outbox event row into the database.
     * Uses MANDATORY / REQUIRED propagation to ensure it executes within the surrounding
     * business transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent saveEvent(String aggregateType, Long aggregateId, String eventType, Object payloadObject) {
        UUID eventId = extractEventId(payloadObject);
        return saveEvent(eventId, aggregateType, aggregateId, eventType, payloadObject);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent saveEvent(UUID eventId, String aggregateType, Long aggregateId, String eventType, Object payloadObject) {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }

        String payloadJson;
        if (payloadObject instanceof String strPayload) {
            payloadJson = strPayload;
        } else {
            try {
                payloadJson = objectMapper.writeValueAsString(payloadObject);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize outbox event payload for {} id={}: {}", aggregateType, aggregateId, e.getMessage());
                throw new IllegalStateException("Failed to serialize outbox payload", e);
            }
        }

        OutboxEvent outboxEvent = new OutboxEvent(eventId, aggregateType, aggregateId, eventType, payloadJson);
        OutboxEvent saved = outboxEventRepository.save(outboxEvent);
        log.debug("Persisted outbox event {} for aggregate {} id={}", eventId, aggregateType, aggregateId);
        return saved;
    }

    private UUID extractEventId(Object payloadObject) {
        if (payloadObject instanceof BookingKafkaEvent bookingKafkaEvent) {
            return bookingKafkaEvent.getEventId();
        }
        return UUID.randomUUID();
    }
}
