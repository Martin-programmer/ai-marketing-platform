-- ============================================================
-- V007__auth_password.sql
-- Add password_hash and display_name columns to user_account
-- for self-issued JWT authentication (pre-Cognito).
-- ============================================================

ALTER TABLE user_account
    ADD COLUMN password_hash  text  NULL,
    ADD COLUMN display_name   text  NULL;

-- Seed existing dev users with a bcrypt hash of 'admin123'
-- BCrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
UPDATE user_account
SET password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    display_name  = CASE email
                        WHEN 'agency_admin@local' THEN 'Agency Admin'
                        WHEN 'agency_user@local'  THEN 'Agency User'
                        WHEN 'owner_admin@local'  THEN 'Owner Admin'
                        ELSE split_part(email, '@', 1)
                    END
WHERE password_hash IS NULL;
