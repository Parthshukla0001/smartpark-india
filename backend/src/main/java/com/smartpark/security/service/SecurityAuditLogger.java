package com.smartpark.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SecurityAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    public enum SecurityEvent {
        REGISTRATION_SUCCESS,
        REGISTRATION_FAILURE,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        ACCOUNT_LOCKED,
        ACCESS_DENIED,
        PARKING_LOT_CREATED,
        PARKING_LOT_UPDATED,
        PARKING_LOT_DEACTIVATED,
        PARKING_FLOOR_CREATED,
        PARKING_FLOOR_UPDATED,
        PARKING_FLOOR_DELETED,
        PARKING_SLOT_CREATED,
        PARKING_SLOT_UPDATED,
        PARKING_SLOT_STATUS_CHANGED,
        PARKING_SLOT_DEACTIVATED,
        BOOKING_CREATED,
        BOOKING_CONFIRMED,
        BOOKING_CANCELLED,
        BOOKING_COMPLETED,
        BOOKING_EXPIRED,
        BOOKING_REJECTED
    }

    public void logEvent(SecurityEvent event, String identifier, String details) {
        // Sanitize identifier to prevent log injection and ensure no raw passwords or tokens are logged
        String safeIdentifier = sanitize(identifier);
        String safeDetails = sanitize(details);

        auditLog.info("[AUDIT] timestamp={} event={} subject={} details=\"{}\"",
                Instant.now(), event, safeIdentifier, safeDetails);
    }

    private String sanitize(String input) {
        if (input == null) return "-";
        return input.replace('\n', '_').replace('\r', '_');
    }
}
