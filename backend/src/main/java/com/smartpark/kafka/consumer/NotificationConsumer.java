package com.smartpark.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.entity.Notification;
import com.smartpark.entity.ProcessedEvent;
import com.smartpark.entity.User;
import com.smartpark.kafka.event.BookingKafkaEvent;
import com.smartpark.repository.NotificationRepository;
import com.smartpark.repository.ProcessedEventRepository;
import com.smartpark.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Asynchronous Kafka consumer that processes booking events and delivers user notifications.
 * Implements strict consumer idempotency via the processed_events table to safeguard
 * against duplicate message delivery from Kafka.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationConsumer(ObjectMapper objectMapper,
                                ProcessedEventRepository processedEventRepository,
                                NotificationRepository notificationRepository,
                                UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @KafkaListener(
            topics = "${smartpark.kafka.topics.booking-events:smartpark.booking.events}",
            groupId = "${smartpark.kafka.consumer-group:smartpark-notification-group}"
    )
    @Transactional
    public void consumeBookingEvent(String message) throws JsonProcessingException {
        log.info("Received Kafka message: {}", message);

        BookingKafkaEvent event = objectMapper.readValue(message, BookingKafkaEvent.class);
        if (event == null || event.getEventId() == null) {
            log.warn("Received invalid or null event, skipping");
            return;
        }

        // Idempotency check: guard against duplicate message delivery
        if (processedEventRepository.existsById(event.getEventId())) {
            log.info("Event {} of type {} has already been processed. Skipping duplicate.",
                    event.getEventId(), event.getEventType());
            return;
        }

        // Create user notification if user exists
        if (event.getUserId() != null) {
            Optional<User> userOptional = userRepository.findById(event.getUserId());
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                String notificationMessage = buildNotificationMessage(event);
                Notification notification = new Notification(
                        user,
                        event.getEventId(),
                        event.getEventType(),
                        notificationMessage
                );
                notificationRepository.save(notification);
                log.debug("Saved notification for user id={}", user.getId());
            } else {
                log.warn("User id={} not found for event {}, notification skipped",
                        event.getUserId(), event.getEventId());
            }
        }

        // Mark event as processed atomically in the same database transaction
        ProcessedEvent processedEvent = new ProcessedEvent(event.getEventId(), event.getEventType());
        processedEventRepository.save(processedEvent);

        log.info("Successfully processed event {} of type {} for booking {}",
                event.getEventId(), event.getEventType(), event.getBookingId());
    }

    private String buildNotificationMessage(BookingKafkaEvent event) {
        String eventType = event.getEventType() != null ? event.getEventType() : "UNKNOWN";
        return switch (eventType) {
            case "BOOKING_CREATED" ->
                    "Your booking #" + event.getBookingId() + " has been created for slot " +
                            (event.getSlotNumber() != null ? event.getSlotNumber() : "") + ".";
            case "BOOKING_CONFIRMED" ->
                    "Your booking #" + event.getBookingId() + " has been confirmed.";
            case "BOOKING_CANCELLED" ->
                    "Your booking #" + event.getBookingId() + " has been cancelled.";
            case "BOOKING_COMPLETED" ->
                    "Your booking #" + event.getBookingId() + " is completed. Thank you for parking with SmartPark!";
            case "BOOKING_EXPIRED" ->
                    "Your booking #" + event.getBookingId() + " has expired.";
            default ->
                    "Update for booking #" + event.getBookingId() + ": " + eventType;
        };
    }
}
