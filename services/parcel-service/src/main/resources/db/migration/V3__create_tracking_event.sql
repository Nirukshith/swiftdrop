-- M2 tracking package: status changes consumed from parcel.status-changed.v1, kept for
-- SSE replay after a reconnect (Last-Event-ID). See docs/tracking-design.md section 4.4.
-- event_id as primary key makes the insert idempotent: a redelivered event is skipped.
CREATE TABLE IF NOT EXISTS tracking_event (
    event_id        VARCHAR(36)              PRIMARY KEY,
    parcel_id       VARCHAR(36)              NOT NULL,
    tracking_number VARCHAR(32)              NOT NULL,
    sequence        BIGINT                   NOT NULL,
    from_status     VARCHAR(32),
    to_status       VARCHAR(32)              NOT NULL,
    reason          VARCHAR(500),
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tracking_event_tracking_no_seq ON tracking_event (tracking_number, sequence);
CREATE INDEX IF NOT EXISTS idx_tracking_event_parcel_seq ON tracking_event (parcel_id, sequence);
