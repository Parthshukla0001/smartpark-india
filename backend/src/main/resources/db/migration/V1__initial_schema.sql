-- =============================================================================
-- SmartPark India - V1 Initial Schema
-- Flyway Database Migration
-- Phase 1: Core Domain Model & Database Foundation
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ROLES TABLE
-- Lookup table for user authorization roles (DRIVER, OPERATOR, ADMIN)
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_name UNIQUE (name)
);

-- Seed baseline platform roles
INSERT INTO roles (name, description) VALUES
    ('DRIVER', 'Driver user who reserves and parks at parking slots'),
    ('OPERATOR', 'Parking operator who manages parking facilities and slots'),
    ('ADMIN', 'Platform administrator with management access');

-- -----------------------------------------------------------------------------
-- 2. USERS TABLE
-- Platform users (drivers, operators, admins)
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role_id ON users(role_id);

-- -----------------------------------------------------------------------------
-- 3. VEHICLES TABLE
-- Registered vehicles belonging to users (e.g. Indian format MH12AB1234)
-- -----------------------------------------------------------------------------
CREATE TABLE vehicles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    registration_number VARCHAR(20) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vehicles_registration_number UNIQUE (registration_number),
    CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_vehicle_type CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE', 'SUV', 'OTHER'))
);

CREATE INDEX idx_vehicles_user_id ON vehicles(user_id);
CREATE INDEX idx_vehicles_reg_number ON vehicles(registration_number);

-- -----------------------------------------------------------------------------
-- 4. PARKING LOTS TABLE
-- Physical parking facilities located in cities/states across India
-- -----------------------------------------------------------------------------
CREATE TABLE parking_lots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    opening_time TIME,
    closing_time TIME,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_latitude_range CHECK (latitude >= -90.0 AND latitude <= 90.0),
    CONSTRAINT chk_longitude_range CHECK (longitude >= -180.0 AND longitude <= 180.0)
);

CREATE INDEX idx_parking_lots_city ON parking_lots(city);

-- -----------------------------------------------------------------------------
-- 5. PARKING FLOORS TABLE
-- Floors within a parking lot (e.g. Ground, Basement 1, Level 2)
-- -----------------------------------------------------------------------------
CREATE TABLE parking_floors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parking_lot_id BIGINT NOT NULL,
    floor_number INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_parking_floors_lot_number UNIQUE (parking_lot_id, floor_number),
    CONSTRAINT fk_parking_floors_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots(id) ON DELETE RESTRICT
);

CREATE INDEX idx_parking_floors_lot_id ON parking_floors(parking_lot_id);

-- -----------------------------------------------------------------------------
-- 6. PARKING SLOTS TABLE
-- Individual parking bays on a parking floor
-- -----------------------------------------------------------------------------
CREATE TABLE parking_slots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parking_floor_id BIGINT NOT NULL,
    slot_number VARCHAR(50) NOT NULL,
    slot_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_parking_slots_floor_slot UNIQUE (parking_floor_id, slot_number),
    CONSTRAINT fk_parking_slots_floor FOREIGN KEY (parking_floor_id) REFERENCES parking_floors(id) ON DELETE RESTRICT,
    CONSTRAINT chk_slot_type CHECK (slot_type IN ('REGULAR', 'DISABLED', 'EV')),
    CONSTRAINT chk_slot_status CHECK (status IN ('AVAILABLE', 'OUT_OF_SERVICE'))
);

CREATE INDEX idx_parking_slots_floor_id ON parking_slots(parking_floor_id);

-- -----------------------------------------------------------------------------
-- 7. BOOKINGS TABLE
-- Parking slot reservations for a vehicle by a user over a time window
-- -----------------------------------------------------------------------------
CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    parking_slot_id BIGINT NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_slot FOREIGN KEY (parking_slot_id) REFERENCES parking_slots(id) ON DELETE RESTRICT,
    CONSTRAINT chk_booking_time_validity CHECK (end_time > start_time),
    CONSTRAINT chk_booking_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'EXPIRED'))
);

CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_parking_slot_id ON bookings(parking_slot_id);
CREATE INDEX idx_bookings_vehicle_id ON bookings(vehicle_id);
CREATE INDEX idx_bookings_slot_times ON bookings(parking_slot_id, start_time, end_time);
