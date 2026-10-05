package com.smartpark.dto.notification;

import com.smartpark.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public class NotificationResponse {

    private Long id;
    private UUID eventId;
    private String type;
    private String message;
    private Instant createdAt;
    private Instant readAt;

    public NotificationResponse() {
    }

    public NotificationResponse(Long id, UUID eventId, String type, String message, Instant createdAt, Instant readAt) {
        this.id = id;
        this.eventId = eventId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventId(),
                notification.getType(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
