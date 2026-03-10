-- V010: Add optional creative_asset_id to copy_variant (links AI-generated copy to its source image)
ALTER TABLE copy_variant ADD COLUMN IF NOT EXISTS creative_asset_id uuid;

-- Foreign key to creative_asset
ALTER TABLE copy_variant
    ADD CONSTRAINT fk_copy_variant_asset
        FOREIGN KEY (creative_asset_id) REFERENCES creative_asset(id) ON DELETE SET NULL;

-- Index for lookup by asset
CREATE INDEX IF NOT EXISTS idx_copy_variant_asset ON copy_variant(creative_asset_id);
