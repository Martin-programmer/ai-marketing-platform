-- V013: Add questionnaire tracking columns to client_profile
ALTER TABLE client_profile ADD COLUMN IF NOT EXISTS questionnaire_completed boolean DEFAULT false;
ALTER TABLE client_profile ADD COLUMN IF NOT EXISTS questionnaire_completed_at timestamptz NULL;
