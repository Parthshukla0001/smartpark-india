package com.smartpark.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Scheduled component that periodically scans the database for active bookings
 * (CONFIRMED or PENDING) that have elapsed their validity window and updates them to EXPIRED.
 * Operates idempotently, transactionally, and without loading non-eligible records into JVM memory.
 */
@Component
@ConditionalOnProperty(name = "smartpark.booking.expiry-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingService bookingService;
    private final Clock clock;

    public BookingExpiryScheduler(BookingService bookingService, Clock clock) {
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartpark.booking.expiry-check-interval-ms:60000}")
    public void processExpiredBookings() {
        try {
            Instant now = Instant.now(clock);
            int expiredCount = bookingService.expireEligibleBookings(now);
            if (expiredCount > 0) {
                log.info("Booking expiry job completed: transitioned {} bookings to EXPIRED as of {}", expiredCount, now);
            }
        } catch (Exception ex) {
            log.error("Error during scheduled booking expiry execution", ex);
        }
    }
}
