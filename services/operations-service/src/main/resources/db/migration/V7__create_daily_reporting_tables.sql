CREATE TABLE daily_report_aggregate (
    report_date DATE PRIMARY KEY,
    total_deliveries INT NOT NULL DEFAULT 0 CHECK (total_deliveries >= 0),
    successful_deliveries INT NOT NULL DEFAULT 0 CHECK (successful_deliveries >= 0),
    failed_deliveries INT NOT NULL DEFAULT 0 CHECK (failed_deliveries >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE report_processed_event (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    parcel_id VARCHAR(36),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_report_processed_event_parcel ON report_processed_event(parcel_id);

CREATE TABLE report_parcel_sequence (
    parcel_id VARCHAR(36) PRIMARY KEY,
    last_sequence BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
