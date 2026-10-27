-- V2__create_parcels_and_addresses.sql
-- Schema for customer saved addresses, parcels, and status transition history.

CREATE TABLE IF NOT EXISTS addresses (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    label VARCHAR(100) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    district VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_addresses_district CHECK (district IN (
        'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo',
        'Galle', 'Gampaha', 'Hambantota', 'Jaffna', 'Kalutara',
        'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala', 'Mannar',
        'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya',
        'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya'
    ))
);

CREATE INDEX IF NOT EXISTS idx_addresses_customer ON addresses(customer_id);

CREATE TABLE IF NOT EXISTS parcels (
    id VARCHAR(36) PRIMARY KEY,
    tracking_number VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    customer_id VARCHAR(36),
    merchant_id VARCHAR(36),
    courier_id VARCHAR(36),
    pickup_line1 VARCHAR(255) NOT NULL,
    pickup_line2 VARCHAR(255),
    pickup_city VARCHAR(100) NOT NULL,
    pickup_district VARCHAR(50) NOT NULL,
    pickup_postal_code VARCHAR(20) NOT NULL,
    dropoff_line1 VARCHAR(255) NOT NULL,
    dropoff_line2 VARCHAR(255),
    dropoff_city VARCHAR(100) NOT NULL,
    dropoff_district VARCHAR(50) NOT NULL,
    dropoff_postal_code VARCHAR(20) NOT NULL,
    weight_kg NUMERIC(6, 2) NOT NULL,
    size VARCHAR(20) NOT NULL,
    fee NUMERIC(10, 2) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(255),
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(50) NOT NULL,
    notes TEXT,
    proof_recipient_name VARCHAR(150),
    proof_photo_path VARCHAR(500),
    sequence BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_parcels_pickup_district CHECK (pickup_district IN (
        'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo',
        'Galle', 'Gampaha', 'Hambantota', 'Jaffna', 'Kalutara',
        'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala', 'Mannar',
        'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya',
        'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya'
    )),
    CONSTRAINT chk_parcels_dropoff_district CHECK (dropoff_district IN (
        'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo',
        'Galle', 'Gampaha', 'Hambantota', 'Jaffna', 'Kalutara',
        'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala', 'Mannar',
        'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya',
        'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya'
    ))
);

CREATE INDEX IF NOT EXISTS idx_parcels_customer ON parcels(customer_id);
CREATE INDEX IF NOT EXISTS idx_parcels_courier ON parcels(courier_id);
CREATE INDEX IF NOT EXISTS idx_parcels_status ON parcels(status);
CREATE INDEX IF NOT EXISTS idx_parcels_created_at ON parcels(created_at);

CREATE TABLE IF NOT EXISTS status_history (
    id VARCHAR(36) PRIMARY KEY,
    parcel_id VARCHAR(36) NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    changed_by VARCHAR(100),
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_status_history_parcel ON status_history(parcel_id);
