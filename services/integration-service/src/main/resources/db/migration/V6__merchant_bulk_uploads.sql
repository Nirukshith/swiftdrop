CREATE TABLE merchant_bulk_uploads (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    client_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_id, idempotency_key)
);
CREATE INDEX idx_merchant_bulk_rate ON merchant_bulk_uploads (merchant_id, client_id, created_at);

CREATE TABLE merchant_bulk_rows (
    upload_id UUID NOT NULL REFERENCES merchant_bulk_uploads(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    row_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64),
    state VARCHAR(16) NOT NULL CHECK (state IN ('WAITING', 'ATTEMPTING', 'SUCCEEDED', 'FAILED', 'INVALID', 'UNKNOWN')),
    result_json TEXT NOT NULL,
    PRIMARY KEY (upload_id, row_number)
);
CREATE TABLE merchant_bulk_references (
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    external_reference VARCHAR(128) NOT NULL,
    upload_id UUID NOT NULL REFERENCES merchant_bulk_uploads(id) ON DELETE CASCADE,
    row_key VARCHAR(128) NOT NULL,
    PRIMARY KEY (merchant_id, external_reference)
);
