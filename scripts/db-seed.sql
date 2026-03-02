-- =============================================================
-- Seed data for local development. Safe to run multiple times.
-- =============================================================

-- Demo Agency
INSERT INTO agency (id, name, status, plan_code, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo Agency', 'ACTIVE', 'PRO', now(), now())
ON CONFLICT DO NOTHING;

-- Demo Users (fake cognito_sub for local dev)
INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'local-sub-agency-admin', 'agency_admin@local', 'AGENCY_ADMIN', 'ACTIVE', now(), now()),
  ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001', 'local-sub-agency-user',  'agency_user@local',  'AGENCY_USER', 'ACTIVE', now(), now()),
  ('00000000-0000-0000-0000-000000000020', NULL,                                   'local-sub-owner-admin',  'owner_admin@local',  'OWNER_ADMIN', 'ACTIVE', now(), now())
ON CONFLICT DO NOTHING;

-- Demo Client
INSERT INTO client (id, agency_id, name, industry, status, timezone, currency, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000001', 'Demo Client', 'ECOM', 'ACTIVE', 'Europe/Sofia', 'BGN', now(), now())
ON CONFLICT DO NOTHING;
