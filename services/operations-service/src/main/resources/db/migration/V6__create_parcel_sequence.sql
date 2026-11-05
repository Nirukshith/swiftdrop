CREATE TABLE IF NOT EXISTS parcel_sequence (
    parcel_id VARCHAR(36) PRIMARY KEY,
    last_sequence BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_parcel_sequence_parcel_id ON parcel_sequence(parcel_id);

