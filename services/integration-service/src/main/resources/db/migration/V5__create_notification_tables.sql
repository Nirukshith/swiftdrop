-- Flyway migration for integration-service: Notification subsystem & Idempotency
-- Owner: Member M3 (Notifications)

CREATE TABLE IF NOT EXISTS processed_event (
    event_id UUID NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);

CREATE TABLE IF NOT EXISTS notification_log (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    parcel_id VARCHAR(64),
    tracking_number VARCHAR(64),
    channel VARCHAR(16) NOT NULL,
    recipient_target VARCHAR(255) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    sequence BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_notification_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_notification_user ON notification_log (user_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_parcel_seq ON notification_log (parcel_id, sequence);
CREATE INDEX IF NOT EXISTS idx_notification_event ON notification_log (event_id);

CREATE OR REPLACE VIEW notification AS SELECT * FROM notification_log;
CREATE OR REPLACE VIEW notifications AS SELECT * FROM notification_log;

