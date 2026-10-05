-- V4__outbox_and_notifications.sql
-- SmartPark India Phase 9: Transactional Outbox Pattern, Idempotent Event Tracking & Notifications

-- 1. Transactional Outbox table for atomic event recording alongside business data
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT
);

-- Partial index for high-speed scanning of pending unpublished events
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at ASC)
    WHERE published_at IS NULL;

-- 2. Processed events table for consumer idempotency tracking
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Notifications table populated asynchronously by Kafka consumer
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_event_id ON notifications (event_id);
