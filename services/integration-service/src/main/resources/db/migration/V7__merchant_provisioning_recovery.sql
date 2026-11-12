ALTER TABLE merchants ADD COLUMN provisioning_operation_id UUID UNIQUE;
CREATE TABLE merchant_provisioning_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    operation_id UUID NOT NULL,
    replacement_id UUID,
    actor_id VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
