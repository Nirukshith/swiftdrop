-- Migration for Delivery Proof metadata table (M8 Courier Execution)
CREATE TABLE delivery_proof (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL,
    courier_id VARCHAR(64) NOT NULL,
    proof_type VARCHAR(32) NOT NULL,
    proof_url VARCHAR(512) NOT NULL,
    recipient_name VARCHAR(128),
    file_path VARCHAR(512) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    notes TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_proof_parcel_id ON delivery_proof (parcel_id);
CREATE INDEX idx_delivery_proof_courier_id ON delivery_proof (courier_id);
