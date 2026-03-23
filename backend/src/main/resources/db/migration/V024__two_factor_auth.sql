-- V024: Two-factor authentication support
-- Agency-level 2FA enforcement + per-user 2FA state

ALTER TABLE agency ADD COLUMN IF NOT EXISTS two_factor_enabled boolean DEFAULT false;

ALTER TABLE user_account ADD COLUMN IF NOT EXISTS two_factor_enabled boolean DEFAULT false;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS two_factor_code text NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS two_factor_code_expires_at timestamptz NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS two_factor_attempts int DEFAULT 0;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS two_factor_locked_until timestamptz NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS two_factor_resend_count int DEFAULT 0;
