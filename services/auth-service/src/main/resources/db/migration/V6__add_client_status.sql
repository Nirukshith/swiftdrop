-- Migration V6: Add status column to clients table
-- Aligned with Issue #120: Merchant client lifecycle and status tracking

ALTER TABLE clients ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_clients_status ON clients (status);
