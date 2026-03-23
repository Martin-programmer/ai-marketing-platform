-- ============================================================
-- V026: Complete settings seed — fill gaps discovered in audit
-- Uses ON CONFLICT DO NOTHING so existing rows are never touched
-- ============================================================

-- ── ANOMALY (missing keys) ──────────────────────────────────

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('ANOMALY', 'anomaly.cpm.surge.lookback.days', '3', 'INTEGER', 'CPM Surge Lookback Days', 'Number of recent days to average for CPM surge detection')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('ANOMALY', 'anomaly.dedup.hours', '24', 'INTEGER', 'Anomaly Dedup Hours', 'Skip duplicate anomaly of same type/entity within this window')
ON CONFLICT (setting_key) DO NOTHING;

-- ── SECURITY (missing keys) ─────────────────────────────────

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('SECURITY', 'security.2fa.code.length', '6', 'INTEGER', '2FA Code Length', 'Number of digits in the 2FA verification code')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('SECURITY', 'security.2fa.max.resends', '3', 'INTEGER', '2FA Max Resends', 'Maximum code resend attempts per login')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('SECURITY', 'security.2fa.resend.cooldown.seconds', '60', 'INTEGER', '2FA Resend Cooldown (seconds)', 'Minimum seconds between code resend requests')
ON CONFLICT (setting_key) DO NOTHING;

-- ── AI (missing keys) ───────────────────────────────────────

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.context.max.chars', '12000', 'INTEGER', 'AI Context Max Chars', 'Maximum character length for shared AI context')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.campaign.creator.max.tokens', '8192', 'INTEGER', 'Campaign Creator Max Tokens', 'Max tokens for AI campaign proposal generation')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.cross.module.quality.threshold', '75', 'INTEGER', 'Cross-Module Quality Threshold', 'Minimum quality score for creative recommendations')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.cross.module.max.creatives', '3', 'INTEGER', 'Cross-Module Max Creatives', 'Maximum creative suggestions per enrichment')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.cross.module.top.ads', '3', 'INTEGER', 'Cross-Module Top Ads', 'Number of top-performing ads in text summary')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.anthropic.input.price.per.mtok', '3.0', 'DECIMAL', 'Claude Input Price ($/MTok)', 'Claude input price per million tokens')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.anthropic.output.price.per.mtok', '15.0', 'DECIMAL', 'Claude Output Price ($/MTok)', 'Claude output price per million tokens')
ON CONFLICT (setting_key) DO NOTHING;

-- ── BUDGET STRATEGIST ───────────────────────────────────────

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'budget.strategist.increase.roas.threshold', '3.0', 'DECIMAL', 'Budget Increase ROAS Threshold', 'ROAS above which budget increase is suggested')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'budget.strategist.decrease.roas.threshold', '1.0', 'DECIMAL', 'Budget Decrease ROAS Threshold', 'ROAS below which budget decrease is suggested')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'budget.strategist.decrease.min.spend', '50', 'DECIMAL', 'Budget Decrease Min Spend', 'Minimum spend to trigger budget decrease suggestion')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'budget.strategist.pause.min.spend', '100', 'DECIMAL', 'Pause/Restructure Min Spend', 'Minimum spend to suggest pause when zero conversions')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'budget.strategist.diminishing.min.days', '14', 'INTEGER', 'Diminishing Returns Min Days', 'Minimum data days to detect diminishing returns')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'budget.strategist.diminishing.spend.increase.pct', '0.10', 'DECIMAL', 'Diminishing Returns Spend Increase %', 'Spend increase threshold to flag diminishing returns')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'budget.strategist.diminishing.cpa.increase.pct', '0.20', 'DECIMAL', 'Diminishing Returns CPA Increase %', 'CPA increase threshold to flag diminishing returns')
ON CONFLICT (setting_key) DO NOTHING;

-- ── AGENCY INTELLIGENCE ─────────────────────────────────────

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'agency.intelligence.sync.freshness.hours', '48', 'INTEGER', 'Sync Freshness Window (hours)', 'Hours within which a sync is considered fresh')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'agency.intelligence.roas.cap', '5.0', 'DECIMAL', 'ROAS Cap for Health Score', 'ROAS value that equals 100% in health score calculation')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'agency.intelligence.health.weight.active', '0.25', 'DECIMAL', 'Health Weight: Active Clients', 'Weight of active client percentage in health score')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'agency.intelligence.health.weight.roas', '0.25', 'DECIMAL', 'Health Weight: ROAS', 'Weight of ROAS score in health score')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'agency.intelligence.health.weight.sync', '0.25', 'DECIMAL', 'Health Weight: Sync Freshness', 'Weight of sync freshness in health score')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'agency.intelligence.health.weight.adoption', '0.25', 'DECIMAL', 'Health Weight: Adoption', 'Weight of suggestion adoption in health score')
ON CONFLICT (setting_key) DO NOTHING;

-- ── CLIENT BRIEFER ──────────────────────────────────────────

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'client.briefer.jsoup.timeout.ms', '10000', 'INTEGER', 'Website Scrape Timeout (ms)', 'Jsoup connection timeout for website analysis')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'client.briefer.max.text.chars', '5000', 'INTEGER', 'Website Max Text Chars', 'Maximum characters to extract from client website')
ON CONFLICT (setting_key) DO NOTHING;

-- ── PUBLISH DEFAULTS ────────────────────────────────────────

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('META', 'publish.default.country', 'BG', 'STRING', 'Default Geo Country', 'Default country code when targeting has no geo_locations')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('META', 'publish.default.timezone', 'Europe/Sofia', 'STRING', 'Default Client Timezone', 'Fallback timezone for clients without one configured')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('META', 'publish.default.conversion.event', 'PURCHASE', 'STRING', 'Default Conversion Event', 'Default Meta pixel conversion event type')
ON CONFLICT (setting_key) DO NOTHING;
