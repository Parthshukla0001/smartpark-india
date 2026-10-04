-- =============================================================================
-- SmartPark India - V3 Booking Lifecycle Indexes
-- Flyway Database Migration
-- Phase 6: Booking Lifecycle Management & Expiry Optimization
-- =============================================================================

-- Partial index on active bookings (CONFIRMED and PENDING) by end_time and start_time
-- to make automated expiry queries sub-millisecond even under millions of total records.
CREATE INDEX idx_bookings_active_expiry
ON bookings (end_time, start_time)
WHERE (status IN ('CONFIRMED', 'PENDING'));
