CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    served BOOLEAN NOT NULL,
    base_fee_lkr NUMERIC(12, 2) NOT NULL CHECK (base_fee_lkr >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE zone_districts (
    zone_id UUID NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    district VARCHAR(64) NOT NULL,
    PRIMARY KEY (zone_id, district),
    UNIQUE (district)
);

CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('WEIGHT_BAND', 'SIZE')),
    rule_key VARCHAR(64) NOT NULL,
    minimum_weight_kg NUMERIC(10, 3),
    maximum_weight_kg NUMERIC(10, 3),
    charge_lkr NUMERIC(12, 2) NOT NULL CHECK (charge_lkr >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (kind = 'SIZE' OR (minimum_weight_kg IS NOT NULL AND maximum_weight_kg IS NOT NULL
        AND minimum_weight_kg >= 0 AND maximum_weight_kg > minimum_weight_kg)),
    UNIQUE (kind, rule_key)
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64),
    details JSONB
);

CREATE INDEX idx_operations_audit_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX idx_operations_audit_target ON audit_log (target_type, target_id);
