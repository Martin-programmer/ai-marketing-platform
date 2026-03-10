-- V012: Add invitation and password-reset token columns to user_account
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS invitation_token text NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS invitation_expires_at timestamptz NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS password_reset_token text NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS password_reset_expires_at timestamptz NULL;
