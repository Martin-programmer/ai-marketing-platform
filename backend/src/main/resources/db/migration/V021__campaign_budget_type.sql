ALTER TABLE campaign
    ADD COLUMN IF NOT EXISTS budget_type text DEFAULT 'ABO';

ALTER TABLE campaign
    ADD COLUMN IF NOT EXISTS daily_budget numeric(18,6) NULL;

UPDATE campaign
SET budget_type = 'ABO'
WHERE budget_type IS NULL;
