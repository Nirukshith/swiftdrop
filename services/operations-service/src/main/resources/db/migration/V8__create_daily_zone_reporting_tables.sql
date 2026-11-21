CREATE TABLE daily_zone_report_aggregate (
    id UUID PRIMARY KEY,
    report_date DATE NOT NULL,
    zone_name VARCHAR(128) NOT NULL,
    total_deliveries INT NOT NULL DEFAULT 0 CHECK (total_deliveries >= 0),
    successful_deliveries INT NOT NULL DEFAULT 0 CHECK (successful_deliveries >= 0),
    failed_deliveries INT NOT NULL DEFAULT 0 CHECK (failed_deliveries >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_zone UNIQUE (report_date, zone_name)
);

CREATE INDEX idx_daily_zone_report_date ON daily_zone_report_aggregate(report_date);
CREATE INDEX idx_daily_zone_name ON daily_zone_report_aggregate(zone_name);

