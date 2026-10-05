package com.smartpark.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Centralized application metrics for SmartPark.
 * Uses Micrometer to track key business events, rate limit rejections,
 * outbox operations, and booking latency.
 *
 * GUARANTEE: Zero high-cardinality labels. NEVER includes user IDs, emails,
 * booking IDs, IP addresses, or JWT tokens in metric labels to avoid
 * metric cardinality explosion.
 */
@Component
public class SmartParkMetrics {

    private final MeterRegistry registry;

    private final Counter bookingsCreated;
    private final Counter bookingsCancelled;
    private final Counter bookingsCompleted;
    private final Counter bookingsExpired;
    private final Counter bookingsConflicts;
    private final Counter rateLimitRejected;
    private final Counter outboxPublished;
    private final Counter outboxFailed;
    private final Counter outboxRetries;
    private final Timer bookingCreationTimer;

    public SmartParkMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.bookingsCreated = Counter.builder("smartpark.bookings.created")
                .description("Total number of bookings successfully created")
                .register(registry);

        this.bookingsCancelled = Counter.builder("smartpark.bookings.cancelled")
                .description("Total number of bookings cancelled")
                .register(registry);

        this.bookingsCompleted = Counter.builder("smartpark.bookings.completed")
                .description("Total number of bookings completed")
                .register(registry);

        this.bookingsExpired = Counter.builder("smartpark.bookings.expired")
                .description("Total number of bookings expired")
                .register(registry);

        this.bookingsConflicts = Counter.builder("smartpark.bookings.conflicts")
                .description("Total number of booking conflicts encountered")
                .register(registry);

        this.rateLimitRejected = Counter.builder("smartpark.rate_limit.rejected")
                .description("Total number of requests rejected by rate limiting")
                .register(registry);

        this.outboxPublished = Counter.builder("smartpark.outbox.published")
                .description("Total number of outbox events published to Kafka")
                .register(registry);

        this.outboxFailed = Counter.builder("smartpark.outbox.failed")
                .description("Total number of outbox events that failed to publish to Kafka")
                .register(registry);

        this.outboxRetries = Counter.builder("smartpark.outbox.retries")
                .description("Total number of outbox event retry attempts")
                .register(registry);

        this.bookingCreationTimer = Timer.builder("smartpark.bookings.creation.time")
                .description("Latency of booking creation requests")
                .register(registry);
    }

    public void incrementBookingsCreated() {
        bookingsCreated.increment();
    }

    public void incrementBookingsCancelled() {
        bookingsCancelled.increment();
    }

    public void incrementBookingsCompleted() {
        bookingsCompleted.increment();
    }

    public void incrementBookingsExpired() {
        bookingsExpired.increment();
    }

    public void incrementBookingsExpired(int count) {
        if (count > 0) {
            bookingsExpired.increment(count);
        }
    }

    public void incrementBookingsConflicts() {
        bookingsConflicts.increment();
    }

    public void incrementRateLimitRejected() {
        rateLimitRejected.increment();
    }

    public void incrementOutboxPublished(int count) {
        if (count > 0) {
            outboxPublished.increment(count);
        }
    }

    public void incrementOutboxFailed() {
        outboxFailed.increment();
    }

    public void incrementOutboxRetries() {
        outboxRetries.increment();
    }

    public void recordBookingCreationTime(long durationNanos) {
        bookingCreationTimer.record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public <T> T recordBookingCreation(Supplier<T> supplier) {
        return bookingCreationTimer.record(supplier);
    }

    public void recordBookingCreation(Runnable runnable) {
        bookingCreationTimer.record(runnable);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
