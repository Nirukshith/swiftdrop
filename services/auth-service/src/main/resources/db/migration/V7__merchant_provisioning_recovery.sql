CREATE TABLE merchant_provisioning (
    operation_id UUID PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id VARCHAR(64) NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    actor_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE merchant_credential_replacements (
    replacement_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES merchant_provisioning(operation_id) ON DELETE CASCADE,
    actor_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE merchant_provisioning_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES merchant_provisioning(operation_id) ON DELETE CASCADE,
    replacement_id UUID,
    actor_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
