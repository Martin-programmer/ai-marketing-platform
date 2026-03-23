-- V027: Auth rate limits and password reset throttling
-- Uses next available Flyway version because V023 already exists.

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('SECURITY', 'security.password.reset.max.per.hour', '3', 'INTEGER', 'Password Reset Max/Hour', 'Max password reset requests per email per hour'),
('SECURITY', 'security.password.reset.cooldown.seconds', '60', 'INTEGER', 'Password Reset Cooldown (sec)', 'Min seconds between reset requests for same email'),
('SECURITY', 'security.2fa.resend.cooldown.seconds', '60', 'INTEGER', '2FA Resend Cooldown (sec)', 'Min seconds between 2FA code resends'),
('SECURITY', 'security.2fa.max.resends', '3', 'INTEGER', '2FA Max Resends', 'Max code resends per login attempt'),
('SECURITY', 'security.login.max.attempts.per.email', '5', 'INTEGER', 'Login Max Attempts/Email', 'Max failed logins per email before temp lock'),
('SECURITY', 'security.login.lockout.minutes', '15', 'INTEGER', 'Login Lockout (minutes)', 'Lockout duration after max failed attempts'),
('SECURITY', 'security.login.max.attempts.per.ip.hour', '20', 'INTEGER', 'Login Max Attempts/IP/Hour', 'Max login attempts from one IP per hour')
ON CONFLICT (setting_key) DO NOTHING;

ALTER TABLE user_account ADD COLUMN IF NOT EXISTS failed_login_attempts int DEFAULT 0;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS locked_until timestamptz NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS last_password_reset_request_at timestamptz NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS password_reset_count_hourly int DEFAULT 0;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS password_reset_count_reset_at timestamptz NULL;
