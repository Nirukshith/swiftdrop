-- Commit the exact fee and reference before a single-order remote write. Unknown
-- outcomes retain this reservation so a retry cannot reprice or create a duplicate.
CREATE TABLE merchant_order_attempts (
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    external_reference VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    quoted_fee NUMERIC(12, 2) NOT NULL CHECK (quoted_fee >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (merchant_id, idempotency_key),
    UNIQUE (merchant_id, external_reference)
);
