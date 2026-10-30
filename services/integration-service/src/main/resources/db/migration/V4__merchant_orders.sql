CREATE TABLE merchant_orders (
    order_id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE RESTRICT,
    parcel_id UUID NOT NULL UNIQUE,
    tracking_number VARCHAR(64) NOT NULL,
    external_reference VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    fee NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_merchant_orders_reference UNIQUE (merchant_id, external_reference),
    CONSTRAINT chk_merchant_orders_status CHECK (status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_merchant_orders_merchant_created
    ON merchant_orders (merchant_id, created_at DESC);

CREATE INDEX idx_merchant_orders_status
    ON merchant_orders (status);
