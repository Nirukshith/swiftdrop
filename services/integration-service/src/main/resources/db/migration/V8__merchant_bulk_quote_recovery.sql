ALTER TABLE merchant_bulk_rows ADD COLUMN quoted_fee NUMERIC CHECK (quoted_fee >= 0);
-- Legacy ambiguous rows lack the original quote; never opt them into blind replay.
ALTER TABLE merchant_bulk_rows ADD COLUMN recovery_version INTEGER NOT NULL DEFAULT 0 CHECK (recovery_version IN (0,1));
ALTER TABLE merchant_bulk_rows DROP CONSTRAINT merchant_bulk_rows_state_check;
ALTER TABLE merchant_bulk_rows ADD CONSTRAINT merchant_bulk_rows_state_check
    CHECK (state IN ('WAITING','ATTEMPTING','SUCCEEDED','FAILED','INVALID','UNKNOWN','RETRYABLE'));
