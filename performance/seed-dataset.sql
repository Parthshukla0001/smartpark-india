-- =============================================================================
-- SmartPark India - Benchmark Dataset Seeding Script
-- Phase 14: Performance & Load Testing
-- =============================================================================

BEGIN;

-- 1. Benchmark Users
-- 100 Drivers (driver1@smartpark.in .. driver100@smartpark.in)
-- 10 Operators (operator1@smartpark.in .. operator10@smartpark.in)
-- 1 Admin (admin@smartpark.in)
-- Password for all: Password123!
-- Password Hash: $2a$10$SEnxHGJrZYxZCMExpBNVE.YQ2m95XCLkLYQxOkPBU.lWEPCHSE.zC

INSERT INTO users (first_name, last_name, email, phone_number, password_hash, role_id, active, created_at, updated_at)
SELECT 
    'Driver' || g,
    'Benchmark',
    'driver' || g || '@smartpark.in',
    '98765' || LPAD(g::text, 5, '0'),
    '$2a$10$SEnxHGJrZYxZCMExpBNVE.YQ2m95XCLkLYQxOkPBU.lWEPCHSE.zC',
    1, -- DRIVER
    true,
    NOW() - INTERVAL '60 days',
    NOW() - INTERVAL '60 days'
FROM generate_series(1, 100) AS g
ON CONFLICT (email) DO UPDATE SET active = true, password_hash = EXCLUDED.password_hash;

INSERT INTO users (first_name, last_name, email, phone_number, password_hash, role_id, active, created_at, updated_at)
SELECT 
    'Operator' || g,
    'Benchmark',
    'operator' || g || '@smartpark.in',
    '98764' || LPAD(g::text, 5, '0'),
    '$2a$10$SEnxHGJrZYxZCMExpBNVE.YQ2m95XCLkLYQxOkPBU.lWEPCHSE.zC',
    2, -- OPERATOR
    true,
    NOW() - INTERVAL '60 days',
    NOW() - INTERVAL '60 days'
FROM generate_series(1, 10) AS g
ON CONFLICT (email) DO UPDATE SET active = true, password_hash = EXCLUDED.password_hash;

INSERT INTO users (first_name, last_name, email, phone_number, password_hash, role_id, active, created_at, updated_at)
VALUES 
    ('Admin', 'Benchmark', 'admin@smartpark.in', '9876599999', '$2a$10$SEnxHGJrZYxZCMExpBNVE.YQ2m95XCLkLYQxOkPBU.lWEPCHSE.zC', 3, true, NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days')
ON CONFLICT (email) DO UPDATE SET active = true, password_hash = EXCLUDED.password_hash;

-- 2. Benchmark Vehicles (200 vehicles assigned across the 100 drivers)
INSERT INTO vehicles (user_id, registration_number, vehicle_type, active, created_at, updated_at)
SELECT 
    u.id,
    'OD02BM' || LPAD((row_number() OVER())::text, 4, '0'),
    CASE (row_number() OVER() % 3)
        WHEN 0 THEN 'CAR'
        WHEN 1 THEN 'SUV'
        ELSE 'MOTORCYCLE'
    END,
    true,
    NOW() - INTERVAL '50 days',
    NOW() - INTERVAL '50 days'
FROM (
    SELECT id FROM users WHERE email LIKE 'driver%@smartpark.in' ORDER BY id LIMIT 100
) u
CROSS JOIN generate_series(1, 2) AS v
ON CONFLICT (registration_number) DO NOTHING;

-- 3. Benchmark Parking Lots (20 facilities across major Indian cities)
INSERT INTO parking_lots (name, address, city, state, pincode, latitude, longitude, opening_time, closing_time, active, created_at, updated_at)
SELECT
    lot_data.name,
    lot_data.address,
    lot_data.city,
    lot_data.state,
    lot_data.pincode,
    lot_data.lat,
    lot_data.lng,
    '06:00:00'::time,
    '23:00:00'::time,
    true,
    NOW() - INTERVAL '40 days',
    NOW() - INTERVAL '40 days'
FROM (VALUES
    ('Forum Mall Smart Parking', 'NH16 Rasulgarh', 'Bhubaneswar', 'Odisha', '751010', 20.2961000, 85.8245000),
    ('Esplanade One Multi-Level Deck', 'Rasulgarh Square', 'Bhubaneswar', 'Odisha', '751010', 20.3012000, 85.8312000),
    ('Infocity Tech Bay', 'Infocity Rd, Patia', 'Bhubaneswar', 'Odisha', '751024', 20.3588000, 85.8160000),
    ('DN Regalia Center', 'South City, Kalinga Nagar', 'Bhubaneswar', 'Odisha', '751019', 20.2450000, 85.7890000),
    ('Indiranagar Metro Hub', '100 Feet Rd, Indiranagar', 'Bengaluru', 'Karnataka', '560038', 12.9784000, 77.6408000),
    ('Whitefield IT Park Bay', 'ITPL Main Rd, Whitefield', 'Bengaluru', 'Karnataka', '560066', 12.9850000, 77.7310000),
    ('Koramangala Commercial Center', '80 Feet Rd, 4th Block', 'Bengaluru', 'Karnataka', '560034', 12.9352000, 77.6245000),
    ('Electronic City Phase 1 Deck', 'Hosur Road, Phase 1', 'Bengaluru', 'Karnataka', '560100', 12.8452000, 77.6602000),
    ('BKC Corporate Smart Park', 'G Block, Bandra Kurla Complex', 'Mumbai', 'Maharashtra', '400051', 19.0657000, 72.8687000),
    ('Lower Parel Galleria Deck', 'Senapati Bapat Marg', 'Mumbai', 'Maharashtra', '400013', 18.9950000, 72.8250000),
    ('Andheri Station West Hub', 'SV Road, Andheri West', 'Mumbai', 'Maharashtra', '400058', 19.1197000, 72.8464000),
    ('Powai Central Tech Bay', 'Hiranandani Gardens, Powai', 'Mumbai', 'Maharashtra', '400076', 19.1176000, 72.9060000),
    ('Hitec City Cyber Bay', 'Madhapur, Hitec City', 'Hyderabad', 'Telangana', '500081', 17.4435000, 78.3772000),
    ('Gachibowli Stadium Multi-Level', 'Old Mumbai Hwy, Gachibowli', 'Hyderabad', 'Telangana', '500032', 17.4401000, 78.3489000),
    ('Banjara Hills Galleria Deck', 'Road No 1, Banjara Hills', 'Hyderabad', 'Telangana', '500034', 17.4156000, 78.4482000),
    ('Hinjawadi Phase 1 Tech Lot', 'Rajiv Gandhi Infotech Park', 'Pune', 'Maharashtra', '411057', 18.5913000, 73.7389000),
    ('Viman Nagar Smart Bay', 'Symbiosis Rd, Viman Nagar', 'Pune', 'Maharashtra', '411014', 18.5679000, 73.9143000),
    ('Shivajinagar Station Deck', 'FC Road, Shivajinagar', 'Pune', 'Maharashtra', '411005', 18.5314000, 73.8446000),
    ('Cyber City Multi-Level Deck', 'DLF Phase 2, Sector 24', 'Gurugram', 'Haryana', '122002', 28.4950000, 77.0890000),
    ('Connaught Place Inner Circle Bay', 'Block B, Connaught Place', 'New Delhi', 'Delhi', '110001', 28.6315000, 77.2167000)
) AS lot_data(name, address, city, state, pincode, lat, lng)
WHERE NOT EXISTS (SELECT 1 FROM parking_lots WHERE name = lot_data.name);

-- 4. Benchmark Floors (3 floors per benchmark lot)
INSERT INTO parking_floors (parking_lot_id, floor_number, name, created_at, updated_at)
SELECT 
    pl.id,
    f.num,
    f.name,
    NOW() - INTERVAL '35 days',
    NOW() - INTERVAL '35 days'
FROM parking_lots pl
CROSS JOIN (VALUES 
    (1, 'Basement 1 (B1)'),
    (2, 'Ground Level (G)'),
    (3, 'Level 1 (P1)')
) AS f(num, name)
WHERE pl.name IN (
    'Forum Mall Smart Parking', 'Esplanade One Multi-Level Deck', 'Infocity Tech Bay', 'DN Regalia Center',
    'Indiranagar Metro Hub', 'Whitefield IT Park Bay', 'Koramangala Commercial Center', 'Electronic City Phase 1 Deck',
    'BKC Corporate Smart Park', 'Lower Parel Galleria Deck', 'Andheri Station West Hub', 'Powai Central Tech Bay',
    'Hitec City Cyber Bay', 'Gachibowli Stadium Multi-Level', 'Banjara Hills Galleria Deck', 'Hinjawadi Phase 1 Tech Lot',
    'Viman Nagar Smart Bay', 'Shivajinagar Station Deck', 'Cyber City Multi-Level Deck', 'Connaught Place Inner Circle Bay'
)
ON CONFLICT (parking_lot_id, floor_number) DO NOTHING;

-- 5. Benchmark Parking Slots (25 slots per floor = 1,500 slots)
INSERT INTO parking_slots (parking_floor_id, slot_number, slot_type, status, created_at, updated_at)
SELECT 
    pf.id,
    'S-' || pf.floor_number || LPAD(s.num::text, 2, '0'),
    CASE 
        WHEN s.num <= 18 THEN 'REGULAR'
        WHEN s.num <= 22 THEN 'EV'
        ELSE 'DISABLED'
    END,
    'AVAILABLE',
    NOW() - INTERVAL '30 days',
    NOW() - INTERVAL '30 days'
FROM parking_floors pf
JOIN parking_lots pl ON pf.parking_lot_id = pl.id
CROSS JOIN generate_series(1, 25) AS s(num)
WHERE pl.name IN (
    'Forum Mall Smart Parking', 'Esplanade One Multi-Level Deck', 'Infocity Tech Bay', 'DN Regalia Center',
    'Indiranagar Metro Hub', 'Whitefield IT Park Bay', 'Koramangala Commercial Center', 'Electronic City Phase 1 Deck',
    'BKC Corporate Smart Park', 'Lower Parel Galleria Deck', 'Andheri Station West Hub', 'Powai Central Tech Bay',
    'Hitec City Cyber Bay', 'Gachibowli Stadium Multi-Level', 'Banjara Hills Galleria Deck', 'Hinjawadi Phase 1 Tech Lot',
    'Viman Nagar Smart Bay', 'Shivajinagar Station Deck', 'Cyber City Multi-Level Deck', 'Connaught Place Inner Circle Bay'
)
ON CONFLICT (parking_floor_id, slot_number) DO NOTHING;

-- 6. Historical Bookings (10,000 completed/cancelled/expired records over the past 30 days)
DO $$
DECLARE
    current_count BIGINT;
    user_ids BIGINT[];
    vehicle_ids BIGINT[];
    slot_ids BIGINT[];
BEGIN
    SELECT count(*) INTO current_count FROM bookings;
    IF current_count < 10000 THEN
        SELECT array_agg(id) INTO user_ids FROM (SELECT id FROM users WHERE email LIKE 'driver%@smartpark.in' LIMIT 100) t;
        SELECT array_agg(id) INTO vehicle_ids FROM (SELECT id FROM vehicles WHERE registration_number LIKE 'OD02BM%' LIMIT 200) t;
        SELECT array_agg(id) INTO slot_ids FROM (SELECT id FROM parking_slots ORDER BY id LIMIT 1500) t;

        INSERT INTO bookings (user_id, vehicle_id, parking_slot_id, start_time, end_time, status, created_at, updated_at)
        SELECT 
            user_ids[1 + (g % array_length(user_ids, 1))],
            vehicle_ids[1 + (g % array_length(vehicle_ids, 1))],
            slot_ids[1 + (g % array_length(slot_ids, 1))],
            NOW() - INTERVAL '30 days' + (g * INTERVAL '3 minutes'),
            NOW() - INTERVAL '30 days' + (g * INTERVAL '3 minutes') + INTERVAL '2 hours',
            CASE (g % 10)
                WHEN 0 THEN 'CANCELLED'
                WHEN 1 THEN 'EXPIRED'
                ELSE 'COMPLETED'
            END,
            NOW() - INTERVAL '30 days' + (g * INTERVAL '3 minutes'),
            NOW() - INTERVAL '30 days' + (g * INTERVAL '3 minutes') + INTERVAL '2 hours'
        FROM generate_series(1, 10000) AS g;
    END IF;
END $$;

-- 7. Active / Upcoming Bookings (500 non-overlapping reservations in the future)
-- Strictly 1 reservation per slot on the first 500 slots: tomorrow 10:00 to 12:00
-- Perfectly satisfies PostgreSQL GiST exclusion constraint
DO $$
DECLARE
    active_count BIGINT;
    user_ids BIGINT[];
    vehicle_ids BIGINT[];
    slot_ids BIGINT[];
BEGIN
    SELECT count(*) INTO active_count FROM bookings WHERE status IN ('CONFIRMED', 'PENDING');
    IF active_count < 500 THEN
        SELECT array_agg(id) INTO user_ids FROM (SELECT id FROM users WHERE email LIKE 'driver%@smartpark.in' LIMIT 100) t;
        SELECT array_agg(id) INTO vehicle_ids FROM (SELECT id FROM vehicles WHERE registration_number LIKE 'OD02BM%' LIMIT 200) t;
        SELECT array_agg(id) INTO slot_ids FROM (SELECT id FROM parking_slots ORDER BY id LIMIT 500) t;

        INSERT INTO bookings (user_id, vehicle_id, parking_slot_id, start_time, end_time, status, created_at, updated_at)
        SELECT 
            user_ids[1 + (g % array_length(user_ids, 1))],
            vehicle_ids[1 + (g % array_length(vehicle_ids, 1))],
            slot_ids[g],
            NOW() + INTERVAL '24 hours' + ((g % 4) * INTERVAL '2 hours'),
            NOW() + INTERVAL '26 hours' + ((g % 4) * INTERVAL '2 hours'),
            'CONFIRMED',
            NOW() - INTERVAL '1 hour',
            NOW() - INTERVAL '1 hour'
        FROM generate_series(1, 500) AS g;
    END IF;
END $$;

-- 8. Notifications for the historical bookings
DO $$
DECLARE
    notif_count BIGINT;
BEGIN
    SELECT count(*) INTO notif_count FROM notifications;
    IF notif_count < 10000 THEN
        INSERT INTO notifications (user_id, event_id, type, message, created_at, read_at)
        SELECT 
            b.user_id,
            gen_random_uuid(),
            'BOOKING_NOTIFICATION',
            'Your booking #' || b.id || ' status is ' || b.status,
            b.created_at,
            CASE WHEN b.status = 'COMPLETED' THEN b.updated_at ELSE NULL END
        FROM bookings b
        WHERE NOT EXISTS (
            SELECT 1 FROM notifications n WHERE n.user_id = b.user_id AND n.message LIKE '%' || b.id || '%'
        )
        LIMIT 10000;
    END IF;
END $$;

-- 9. Processed Events for idempotency verification
INSERT INTO processed_events (event_id, event_type, processed_at)
SELECT 
    n.event_id,
    'BOOKING_EVENT',
    n.created_at
FROM notifications n
ON CONFLICT (event_id) DO NOTHING;

COMMIT;

-- Analyze all tables to update PostgreSQL query planner statistics
ANALYZE users;
ANALYZE vehicles;
ANALYZE parking_lots;
ANALYZE parking_floors;
ANALYZE parking_slots;
ANALYZE bookings;
ANALYZE notifications;
ANALYZE processed_events;
