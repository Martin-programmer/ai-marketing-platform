-- ============================================================
-- V006__performance_indices.sql
-- Performance indices for common query patterns
-- (Some may already exist from initial migrations, using IF NOT EXISTS)
-- ============================================================

-- Insight daily: fast KPI aggregation queries
CREATE INDEX IF NOT EXISTS idx_insight_daily_client_date 
    ON insight_daily (client_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_insight_daily_entity_date 
    ON insight_daily (entity_type, entity_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_insight_daily_agency_date 
    ON insight_daily (agency_id, date DESC);

-- Campaigns: filter by client + status (most common query)
CREATE INDEX IF NOT EXISTS idx_campaign_client_status 
    ON campaign (client_id, status);

-- Adsets: lookup by campaign
CREATE INDEX IF NOT EXISTS idx_adset_campaign 
    ON adset (campaign_id);
CREATE INDEX IF NOT EXISTS idx_adset_client_status 
    ON adset (client_id, status);

-- Ads: lookup by adset
CREATE INDEX IF NOT EXISTS idx_ad_adset 
    ON ad (adset_id);

-- Suggestions: filter by client + status (inbox query)
CREATE INDEX IF NOT EXISTS idx_suggestion_client_status_created 
    ON ai_suggestion (client_id, status, created_at DESC);

-- Reports: filter by client + period
CREATE INDEX IF NOT EXISTS idx_report_client_period 
    ON report (client_id, period_start DESC, period_end DESC);

-- Creative assets: filter by client + status
CREATE INDEX IF NOT EXISTS idx_creative_asset_client_status 
    ON creative_asset (client_id, status);

-- Creative packages: filter by client + status
CREATE INDEX IF NOT EXISTS idx_creative_package_client_status 
    ON creative_package (client_id, status);

-- Audit log: fast lookup by correlation
CREATE INDEX IF NOT EXISTS idx_audit_log_correlation 
    ON audit_log (correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_agency_created 
    ON audit_log (agency_id, created_at DESC);

-- Meta connection: lookup by client
CREATE INDEX IF NOT EXISTS idx_meta_connection_client 
    ON meta_connection (client_id);

-- Meta sync jobs: latest job per client
CREATE INDEX IF NOT EXISTS idx_meta_sync_client_requested 
    ON meta_sync_job (client_id, requested_at DESC);

-- Feedback: lookup by source
CREATE INDEX IF NOT EXISTS idx_feedback_source 
    ON feedback (source_type, source_id);
