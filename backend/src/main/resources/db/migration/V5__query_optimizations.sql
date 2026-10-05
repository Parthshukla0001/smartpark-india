-- =============================================================================
-- SmartPark India - V5 Database Query Optimizations & Supporting Indexes
-- Flyway Database Migration
-- Phase 10: Query Optimization & Performance Analysis
-- =============================================================================

-- 1. Composite index for driver booking history pagination and sorting
-- Eliminates top-N heapsort memory allocations and enables streaming index scans
-- for `findByUserId` ordered by start_time DESC.
CREATE INDEX idx_bookings_user_start_time
ON bookings (user_id, start_time DESC);

-- 2. Composite index for driver booking history filtered by status
-- Provides direct multi-column index scan for `findByUserIdAndStatus`
-- ordered by start_time DESC without in-memory sorting.
CREATE INDEX idx_bookings_user_status_start_time
ON bookings (user_id, status, start_time DESC);

-- 3. Functional composite index for case-insensitive city and active status filtering
-- Supports `ParkingLotService.getParkingLots` specification: LOWER(city) = :city AND active = :active
CREATE INDEX idx_parking_lots_lower_city_active
ON parking_lots (LOWER(city), active);

-- 4. Partial index for active slot reservation interval overlap checks
-- Supports application-level availability queries in `BookingRepository.existsByParkingSlotAndTimeOverlap`.
-- By filtering strictly on active reserving statuses (CONFIRMED, PENDING), this partial index
-- excludes millions of historical completed, cancelled, and expired bookings, maintaining
-- a compact footprint and sub-millisecond index scans.
-- NOTE: The Phase 5 GiST exclusion constraint (uq_bookings_no_overlapping_active_slots)
-- remains authoritative for transactional concurrency prevention.
CREATE INDEX idx_bookings_active_slot_times
ON bookings (parking_slot_id, start_time, end_time)
WHERE (status IN ('CONFIRMED', 'PENDING'));
