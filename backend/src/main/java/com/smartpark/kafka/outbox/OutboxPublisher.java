package com.smartpark.kafka.outbox;

import com.smartpark.entity.OutboxEvent;
import com.smartpark.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Outbox Publisher responsible for polling unpublished outbox events from PostgreSQL
 * and publishing them to Kafka. Uses FOR UPDATE SKIP LOCKED to guarantee safe, collision-free
 * concurrent publishing across multiple application instances.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.smartpark.observability.SmartParkMetrics metrics;

    @Value("${smartpark.kafka.topics.booking-events:smartpark.booking.events}")
    private String bookingEventsTopic = "smartpark.booking.events";

    @Value("${smartpark.kafka.outbox.enabled:true}")
    private boolean outboxEnabled = true;

    @Value("${smartpark.kafka.outbox.batch-size:50}")
    private int batchSize = 50;

    @org.springframework.beans.factory.annotation.Autowired
    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.smartpark.observability.SmartParkMetrics metrics) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
    }

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this(outboxEventRepository, kafkaTemplate, null);
    }

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           String bookingEventsTopic,
                           boolean outboxEnabled,
                           int batchSize) {
        this(outboxEventRepository, kafkaTemplate, null);
        this.bookingEventsTopic = bookingEventsTopic;
        this.outboxEnabled = outboxEnabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${smartpark.kafka.outbox.poll-interval-ms:1000}")
    public void pollAndPublish() {
        if (!outboxEnabled) {
            return;
        }
        publishPendingEvents();
    }

    /**
     * Finds pending outbox events using FOR UPDATE SKIP LOCKED, publishes each to Kafka,
     * waits for broker acknowledgement, and marks published_at timestamp upon confirmation.
     *
     * @return count of successfully published events in this iteration
     */
    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForUpdate(batchSize);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        for (OutboxEvent event : pendingEvents) {
            String correlationId = event.getEventId() != null ? event.getEventId().toString() : java.util.UUID.randomUUID().toString();
            org.slf4j.MDC.put("correlationId", correlationId);
            try {
                String key = event.getAggregateId() != null ? String.valueOf(event.getAggregateId()) : null;
                try {
                    CompletableFuture<SendResult<String, String>> future =
                            kafkaTemplate.send(bookingEventsTopic, key, event.getPayload());

                    // Wait for broker ACK before updating outbox status
                    future.get(5, TimeUnit.SECONDS);

                    event.setPublishedAt(Instant.now());
                    event.setLastError(null);
                    publishedCount++;
                    if (metrics != null) {
                        metrics.incrementOutboxPublished(1);
                    }
                    log.debug("Successfully published outbox event {} to topic {}", event.getEventId(), bookingEventsTopic);
                } catch (Exception e) {
                    event.setAttempts(event.getAttempts() + 1);
                    event.setLastError(e.getMessage());
                    if (metrics != null) {
                        metrics.incrementOutboxRetries();
                        metrics.incrementOutboxFailed();
                    }
                    log.warn("Failed to publish outbox event {} to Kafka (attempt {}): {}",
                            event.getEventId(), event.getAttempts(), e.getMessage());
                }
                outboxEventRepository.save(event);
            } finally {
                org.slf4j.MDC.remove("correlationId");
            }
        }

        outboxEventRepository.flush();
        return publishedCount;
    }

    public boolean isOutboxEnabled() {
        return outboxEnabled;
    }

    public void setOutboxEnabled(boolean outboxEnabled) {
        this.outboxEnabled = outboxEnabled;
    }
}
