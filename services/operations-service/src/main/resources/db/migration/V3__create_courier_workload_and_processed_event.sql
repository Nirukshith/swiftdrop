CREATE TABLE IF NOT EXISTS courier_workload (
    courier_id VARCHAR(36) PRIMARY KEY,
    active_count INT NOT NULL DEFAULT 0,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    last_event_id VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_active_count_non_negative CHECK (active_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_courier_workload_active_count ON courier_workload(active_count);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    parcel_id VARCHAR(36),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_event_parcel ON processed_event(parcel_id);
