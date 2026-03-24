ALTER TABLE agency
    ADD COLUMN IF NOT EXISTS slug text NULL;

UPDATE agency
SET slug = lower(slug)
WHERE slug IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_agency_slug
    ON agency (lower(slug))
    WHERE slug IS NOT NULL;
