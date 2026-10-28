CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    business_name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    business_registration_number VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    rejection_reason VARCHAR(1000),
    auth_user_id VARCHAR(64),
    client_id VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_merchants_email UNIQUE (email),
    CONSTRAINT chk_merchants_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

CREATE INDEX idx_merchants_status_created ON merchants (status, created_at DESC);

CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    processing_state VARCHAR(16) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_scope UNIQUE (merchant_id, operation, idempotency_key),
    CONSTRAINT chk_idempotency_state CHECK (processing_state IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
