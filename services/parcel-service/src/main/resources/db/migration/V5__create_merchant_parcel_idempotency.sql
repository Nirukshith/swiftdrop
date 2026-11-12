CREATE TABLE IF NOT EXISTS merchant_parcel_idempotency (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    parcel_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_merchant_parcel_idempotency_scope
        UNIQUE (merchant_id, operation, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_merchant_parcel_idempotency_parcel
    ON merchant_parcel_idempotency(parcel_id);
