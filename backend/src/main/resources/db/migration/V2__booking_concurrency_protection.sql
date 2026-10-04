-- =============================================================================
-- SmartPark India - V2 Booking Concurrency Protection
-- Flyway Database Migration
-- Phase 5: Concurrency & Double-Booking Prevention
-- =============================================================================

-- Enable btree_gist extension to allow scalar types (e.g. BIGINT parking_slot_id)
-- with equality operator (=) in GiST exclusion indexes
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Add partial exclusion constraint to guarantee that no two active reservations
-- (CONFIRMED or PENDING) can occupy the same parking slot for overlapping time intervals.
--
-- Interval semantics: '[)' denotes [start_time, end_time) (inclusive start, exclusive end),
-- allowing adjacent bookings (e.g. 10:00-11:00 and 11:00-12:00) to succeed without conflict.
-- The partial filter WHERE (status IN ('CONFIRMED', 'PENDING')) ensures CANCELLED,
-- COMPLETED, and EXPIRED bookings do not block new reservations.
ALTER TABLE bookings
ADD CONSTRAINT uq_bookings_no_overlapping_active_slots
EXCLUDE USING gist (
    parking_slot_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
)
WHERE (status IN ('CONFIRMED', 'PENDING'));
