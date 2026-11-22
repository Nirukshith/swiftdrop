-- Migration V10: Brute-force protection for 6-digit password reset codes (M3, reset package)
-- Codes are now looked up by the user's active token and compared by hash, so failed guesses
-- can be counted per code. A code is invalidated after too many failed attempts.

ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0;

-- Six-digit codes repeat over time; the hash no longer identifies a token on its own,
-- and a uniqueness constraint would make new reset requests fail on a collision.
ALTER TABLE password_reset_tokens
    DROP CONSTRAINT IF EXISTS password_reset_tokens_token_hash_key;

CREATE INDEX IF NOT EXISTS idx_password_reset_user_created
    ON password_reset_tokens (user_id, created_at DESC);
