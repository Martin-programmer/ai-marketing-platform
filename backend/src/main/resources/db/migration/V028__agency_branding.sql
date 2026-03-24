-- ============================================================
-- V028: Agency branding (white-label) support
-- ============================================================

-- Agency branding settings
ALTER TABLE agency ADD COLUMN IF NOT EXISTS logo_url text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS logo_s3_key text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS primary_color text DEFAULT '#1976D2';
ALTER TABLE agency ADD COLUMN IF NOT EXISTS secondary_color text DEFAULT '#424242';
ALTER TABLE agency ADD COLUMN IF NOT EXISTS accent_color text DEFAULT '#FF9800';
ALTER TABLE agency ADD COLUMN IF NOT EXISTS font_family text DEFAULT 'Roboto, sans-serif';
ALTER TABLE agency ADD COLUMN IF NOT EXISTS company_email text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS company_phone text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS company_website text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS company_address text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS email_footer_text text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS report_footer_text text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS report_disclaimer text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS portal_welcome_message text NULL;
ALTER TABLE agency ADD COLUMN IF NOT EXISTS custom_css text NULL;

-- Platform branding (Adverion defaults) in system_setting
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('BRANDING', 'branding.platform.name', 'Adverion', 'STRING', 'Platform Name', 'Platform name shown to agency users'),
('BRANDING', 'branding.platform.logo.url', '/adverion-logo.png', 'STRING', 'Platform Logo URL', 'Path to Adverion logo file'),
('BRANDING', 'branding.platform.primary.color', '#1976D2', 'STRING', 'Platform Primary Color', 'Main color for Adverion branding'),
('BRANDING', 'branding.platform.secondary.color', '#424242', 'STRING', 'Platform Secondary Color', 'Secondary color'),
('BRANDING', 'branding.platform.accent.color', '#FF9800', 'STRING', 'Platform Accent Color', 'Accent color'),
('BRANDING', 'branding.platform.tagline', 'AI-Powered Marketing Platform', 'STRING', 'Platform Tagline', 'Shown under logo on login'),
('BRANDING', 'branding.platform.footer.text', '© 2026 Adverion. All rights reserved.', 'STRING', 'Platform Footer', 'Footer text for agency-facing pages'),
('BRANDING', 'branding.powered.by.text', 'Powered by Adverion', 'STRING', 'Powered By Text', 'Shown in client portal footer'),
('BRANDING', 'branding.powered.by.visible', 'true', 'BOOLEAN', 'Show Powered By', 'Whether to show Powered by Adverion in client portal')
ON CONFLICT (setting_key) DO NOTHING;
