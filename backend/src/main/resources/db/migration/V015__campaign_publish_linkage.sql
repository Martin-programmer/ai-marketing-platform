-- ============================================================
-- V015__campaign_publish_linkage.sql
-- Persist AI ad creative/copy linkage for campaign publishing
-- ============================================================

ALTER TABLE adset
    ADD COLUMN IF NOT EXISTS optimization_goal text NOT NULL DEFAULT 'CONVERSIONS';

ALTER TABLE ad
    ADD COLUMN IF NOT EXISTS creative_asset_id uuid NULL,
    ADD COLUMN IF NOT EXISTS copy_variant_id uuid NULL,
    ADD COLUMN IF NOT EXISTS primary_text text NULL,
    ADD COLUMN IF NOT EXISTS headline text NULL,
    ADD COLUMN IF NOT EXISTS description text NULL,
    ADD COLUMN IF NOT EXISTS cta text NULL,
    ADD COLUMN IF NOT EXISTS destination_url text NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_ad_creative_asset'
    ) THEN
        ALTER TABLE ad
            ADD CONSTRAINT fk_ad_creative_asset
                FOREIGN KEY (creative_asset_id) REFERENCES creative_asset (id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_ad_copy_variant'
    ) THEN
        ALTER TABLE ad
            ADD CONSTRAINT fk_ad_copy_variant
                FOREIGN KEY (copy_variant_id) REFERENCES copy_variant (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ad_creative_asset_id ON ad (creative_asset_id);
CREATE INDEX IF NOT EXISTS idx_ad_copy_variant_id ON ad (copy_variant_id);
