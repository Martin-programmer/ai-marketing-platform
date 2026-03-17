ALTER TABLE creative_package_item
    ADD COLUMN IF NOT EXISTS cta_type varchar(50),
    ADD COLUMN IF NOT EXISTS destination_url text;