CREATE TABLE courier_profiles (
    user_id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    phone VARCHAR(64),
    zone VARCHAR(128),
    availability BOOLEAN NOT NULL DEFAULT TRUE,
    vehicle_type VARCHAR(64)
);

CREATE INDEX idx_courier_profile_zone ON courier_profiles (zone);
CREATE INDEX idx_courier_profile_available ON courier_profiles (availability);
