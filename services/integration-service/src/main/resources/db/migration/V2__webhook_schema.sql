-- Flyway migration for integration-service: Webhooks, Delivery Logs, and Consumer-Scoped Idempotency
-- Owner: Member M7 (Webhooks & Security)

CREATE TABLE IF NOT EXISTS webhook_endpoints (
    merchant_id UUID PRIMARY KEY,
    url VARCHAR(2048),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    subscribed_events VARCHAR(255) NOT NULL DEFAULT 'PARCEL_CREATED,PARCEL_STATUS_CHANGED',
    encrypted_secret VARCHAR(512) NOT NULL,
    secret_preview VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    delivery_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    tracking_number VARCHAR(128),
    target_url VARCHAR(2048) NOT NULL,
    status VARCHAR(32) NOT NULL,
    http_status_code INT,
    attempts INT NOT NULL DEFAULT 1,
    request_headers TEXT,
    payload TEXT NOT NULL,
    response_headers TEXT,
    response_body TEXT,
    error_summary TEXT,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_merchant_created
    ON webhook_deliveries (merchant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status
    ON webhook_deliveries (status);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_event_id
    ON webhook_deliveries (event_id);

CREATE TABLE IF NOT EXISTS webhook_delivery_attempts (
    id BIGSERIAL PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES webhook_deliveries(delivery_id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    http_status_code INT,
    latency_ms INT NOT NULL,
    success BOOLEAN NOT NULL,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_webhook_attempts_delivery_id
    ON webhook_delivery_attempts (delivery_id);

-- Consumer-scoped idempotency table coordinated with Member M3
CREATE TABLE IF NOT EXISTS processed_event (
    event_id UUID NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);
