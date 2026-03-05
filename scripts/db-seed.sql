-- =============================================================
-- Seed data for local development — REALISTIC multi-agency scenario.
-- Safe to run multiple times (ON CONFLICT DO NOTHING / idempotent).
-- Run: psql -h localhost -U amp -d amp -f scripts/db-seed.sql
-- =============================================================

-- ╔═══════════════════════════════════════════════════════════╗
-- ║  0. CLEAN SLATE (optional — uncomment to wipe first)     ║
-- ╚═══════════════════════════════════════════════════════════╝
-- DELETE FROM feedback; DELETE FROM report; DELETE FROM ai_action_log;
-- DELETE FROM ai_suggestion; DELETE FROM insight_daily; DELETE FROM ad;
-- DELETE FROM adset; DELETE FROM campaign; DELETE FROM meta_sync_job;
-- DELETE FROM meta_connection; DELETE FROM creative_package_item;
-- DELETE FROM creative_package; DELETE FROM creative_analysis;
-- DELETE FROM copy_variant; DELETE FROM creative_asset;
-- DELETE FROM user_client_permission; DELETE FROM client_profile;
-- DELETE FROM audit_log; DELETE FROM user_account WHERE role != 'OWNER_ADMIN';
-- DELETE FROM client; DELETE FROM agency;

-- BCrypt hash of 'admin123'
-- $2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK

-- ══════════════════════════════════════════════════════════════
-- 1. AGENCIES (3)
-- ══════════════════════════════════════════════════════════════
INSERT INTO agency (id, name, status, plan_code, created_at, updated_at) VALUES
  ('a0000000-0000-0000-0000-000000000001', 'BrightWave Digital', 'ACTIVE', 'PRO', now() - interval '6 months', now()),
  ('a0000000-0000-0000-0000-000000000002', 'NexGen Marketing', 'ACTIVE', 'STARTER', now() - interval '3 months', now()),
  ('a0000000-0000-0000-0000-000000000003', 'AdPulse Media', 'ACTIVE', 'PRO', now() - interval '1 month', now())
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 2. USERS
-- ══════════════════════════════════════════════════════════════

-- Owner (platform superadmin — no agency)
INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status, password_hash, display_name, created_at, updated_at) VALUES
  ('a0000000-0000-0000-0000-000000000001', NULL, 'local-owner', 'owner@local', 'OWNER_ADMIN', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Platform Owner', now() - interval '6 months', now())
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, agency_id = NULL;

-- BrightWave Digital — team (3 users)
INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status, password_hash, display_name, created_at, updated_at) VALUES
  ('a0000000-0000-0000-0000-000000000010', 'a0000000-0000-0000-0000-000000000001', 'local-bw-admin', 'maria@brightwave.bg', 'AGENCY_ADMIN', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Maria Ivanova', now() - interval '6 months', now()),
  ('a0000000-0000-0000-0000-000000000011', 'a0000000-0000-0000-0000-000000000001', 'local-bw-user1', 'georgi@brightwave.bg', 'AGENCY_USER', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Georgi Petrov', now() - interval '5 months', now()),
  ('a0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-000000000001', 'local-bw-user2', 'elena@brightwave.bg', 'AGENCY_USER', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Elena Dimitrova', now() - interval '3 months', now())
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, display_name = EXCLUDED.display_name;

-- NexGen Marketing — team (2 users)
INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status, password_hash, display_name, created_at, updated_at) VALUES
  ('a0000000-0000-0000-0000-000000000020', 'a0000000-0000-0000-0000-000000000002', 'local-ng-admin', 'ivan@nexgen.bg', 'AGENCY_ADMIN', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Ivan Kolev', now() - interval '3 months', now()),
  ('a0000000-0000-0000-0000-000000000021', 'a0000000-0000-0000-0000-000000000002', 'local-ng-user1', 'petya@nexgen.bg', 'AGENCY_USER', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Petya Stoyanova', now() - interval '2 months', now())
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, display_name = EXCLUDED.display_name;

-- AdPulse Media — team (2 users)
INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status, password_hash, display_name, created_at, updated_at) VALUES
  ('a0000000-0000-0000-0000-000000000030', 'a0000000-0000-0000-0000-000000000003', 'local-ap-admin', 'stefan@adpulse.bg', 'AGENCY_ADMIN', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Stefan Marinov', now() - interval '1 month', now()),
  ('a0000000-0000-0000-0000-000000000031', 'a0000000-0000-0000-0000-000000000003', 'local-ap-user1', 'ana@adpulse.bg', 'AGENCY_USER', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Ana Todorova', now() - interval '3 weeks', now())
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, display_name = EXCLUDED.display_name;

-- ══════════════════════════════════════════════════════════════
-- 3. CLIENTS (7 across 3 agencies)
-- ══════════════════════════════════════════════════════════════

-- BrightWave Digital clients (3)
INSERT INTO client (id, agency_id, name, industry, status, timezone, currency, created_at, updated_at) VALUES
  ('b0000000-0000-0000-0000-000000000101', 'a0000000-0000-0000-0000-000000000001', 'StyleShop.bg', 'ECOM', 'ACTIVE', 'Europe/Sofia', 'BGN', now() - interval '5 months', now()),
  ('b0000000-0000-0000-0000-000000000102', 'a0000000-0000-0000-0000-000000000001', 'TravelMood', 'TRAVEL', 'ACTIVE', 'Europe/Sofia', 'EUR', now() - interval '4 months', now()),
  ('b0000000-0000-0000-0000-000000000103', 'a0000000-0000-0000-0000-000000000001', 'GreenHome BG', 'HOME', 'PAUSED', 'Europe/Sofia', 'BGN', now() - interval '2 months', now())
ON CONFLICT DO NOTHING;

-- NexGen Marketing clients (2)
INSERT INTO client (id, agency_id, name, industry, status, timezone, currency, created_at, updated_at) VALUES
  ('b0000000-0000-0000-0000-000000000201', 'a0000000-0000-0000-0000-000000000002', 'FitZone Gym', 'FITNESS', 'ACTIVE', 'Europe/Sofia', 'BGN', now() - interval '2 months', now()),
  ('b0000000-0000-0000-0000-000000000202', 'a0000000-0000-0000-0000-000000000002', 'PetPlanet.bg', 'ECOM', 'ACTIVE', 'Europe/Sofia', 'BGN', now() - interval '6 weeks', now())
ON CONFLICT DO NOTHING;

-- AdPulse Media clients (2)
INSERT INTO client (id, agency_id, name, industry, status, timezone, currency, created_at, updated_at) VALUES
  ('b0000000-0000-0000-0000-000000000301', 'a0000000-0000-0000-0000-000000000003', 'LuxDent Clinic', 'HEALTH', 'ACTIVE', 'Europe/Sofia', 'BGN', now() - interval '3 weeks', now()),
  ('b0000000-0000-0000-0000-000000000302', 'a0000000-0000-0000-0000-000000000003', 'AutoElite BG', 'AUTO', 'ACTIVE', 'Europe/Sofia', 'BGN', now() - interval '2 weeks', now())
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 4. CLIENT PROFILES
-- ══════════════════════════════════════════════════════════════
INSERT INTO client_profile (id, agency_id, client_id, website, profile_json, created_at, updated_at) VALUES
  ('b1000000-0000-0000-0000-000000000101', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'https://styleshop.bg',
   '{"usp":"Модерни дрехи и аксесоари на достъпни цени","audiences":["жени 22-40","модни ентусиасти"],"tone":"стилен и вдъхновяващ","offers":["Безплатна доставка над 60 лв","10% при първа поръчка"],"competitors":["zara.bg","aboutyou.bg"]}', now() - interval '5 months', now()),
  ('b1000000-0000-0000-0000-000000000102', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'https://travelmood.bg',
   '{"usp":"Уникални пътувания и преживявания за млади хора","audiences":["25-38 авантюристи","двойки"],"tone":"вдъхновяващ и свободен","offers":["Early bird -15%","Групови отстъпки"],"competitors":["booking.com","lidl-travel.bg"]}', now() - interval '4 months', now()),
  ('b1000000-0000-0000-0000-000000000201', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'https://fitzone.bg',
   '{"usp":"Модерни фитнес зали в София с персонален подход","audiences":["мъже и жени 20-45","фитнес начинаещи"],"tone":"мотивиращ и енергичен","offers":["Първа тренировка безплатно","Месечен абонамент от 49 лв"],"competitors":["nextlevel.bg","pulse-fitness.bg"]}', now() - interval '2 months', now()),
  ('b1000000-0000-0000-0000-000000000202', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000202', 'https://petplanet.bg',
   '{"usp":"Всичко за домашните любимци - храна, аксесоари, ветеринарни съвети","audiences":["собственици на кучета и котки 25-55"],"tone":"грижовен и забавен","offers":["Абонамент с 20% отстъпка","Безплатна доставка над 50 лв"],"competitors":["zooplus.bg","mr.pet.bg"]}', now() - interval '6 weeks', now()),
  ('b1000000-0000-0000-0000-000000000301', 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000301', 'https://luxdent.bg',
   '{"usp":"Висок клас дентални услуги с модерно оборудване","audiences":["хора 30-60 с доходи","естетични процедури"],"tone":"професионален и доверителен","offers":["Безплатен преглед","Разсрочено плащане"],"competitors":["dentalclinic.bg","colgate.bg"]}', now() - interval '3 weeks', now())
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 4b. CLIENT_USER accounts (after clients, due to FK)
-- ══════════════════════════════════════════════════════════════
INSERT INTO user_account (id, agency_id, client_id, cognito_sub, email, role, status, password_hash, display_name, created_at, updated_at) VALUES
  ('a0000000-0000-0000-0000-000000000050', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'local-client-ss', 'client@styleshop.bg', 'CLIENT_USER', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Десислава (StyleShop)', now() - interval '4 months', now()),
  ('a0000000-0000-0000-0000-000000000051', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'local-client-fz', 'client@fitzone.bg', 'CLIENT_USER', 'ACTIVE',
   '$2a$10$x1Wfa1wyYW2/Ncor40PsH.UwIQRrCSHAuuVGLbue7GQvxs3f/uMJK', 'Николай (FitZone)', now() - interval '6 weeks', now())
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, display_name = EXCLUDED.display_name;

-- ══════════════════════════════════════════════════════════════
-- 5. USER-CLIENT PERMISSIONS (for AGENCY_USER)
-- ══════════════════════════════════════════════════════════════

-- Georgi @ BrightWave → StyleShop (EDITOR), TravelMood (READ_ONLY)
INSERT INTO user_client_permission (id, user_id, client_id, permission, granted_by, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'CLIENT_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'CLIENT_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'CAMPAIGNS_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'CAMPAIGNS_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'CREATIVES_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'CREATIVES_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'REPORTS_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'REPORTS_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'AI_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000101', 'AI_APPROVE', 'a0000000-0000-0000-0000-000000000010', now()),
  -- TravelMood: read-only
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000102', 'CLIENT_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000102', 'CAMPAIGNS_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000102', 'REPORTS_VIEW', 'a0000000-0000-0000-0000-000000000010', now())
ON CONFLICT ON CONSTRAINT uq_ucp_user_client_perm DO NOTHING;

-- Elena @ BrightWave → TravelMood (FULL), GreenHome (READ_ONLY)
INSERT INTO user_client_permission (id, user_id, client_id, permission, granted_by, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'CLIENT_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'CLIENT_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'CAMPAIGNS_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'CAMPAIGNS_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'CAMPAIGNS_PUBLISH', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'CREATIVES_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'CREATIVES_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'REPORTS_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'REPORTS_EDIT', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'REPORTS_SEND', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'META_MANAGE', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'AI_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000102', 'AI_APPROVE', 'a0000000-0000-0000-0000-000000000010', now()),
  -- GreenHome: read-only
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000103', 'CLIENT_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000103', 'CAMPAIGNS_VIEW', 'a0000000-0000-0000-0000-000000000010', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000012', 'b0000000-0000-0000-0000-000000000103', 'REPORTS_VIEW', 'a0000000-0000-0000-0000-000000000010', now())
ON CONFLICT ON CONSTRAINT uq_ucp_user_client_perm DO NOTHING;

-- Petya @ NexGen → FitZone (EDITOR), PetPlanet (READ_ONLY)
INSERT INTO user_client_permission (id, user_id, client_id, permission, granted_by, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000201', 'CLIENT_VIEW', 'a0000000-0000-0000-0000-000000000020', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000201', 'CLIENT_EDIT', 'a0000000-0000-0000-0000-000000000020', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000201', 'CAMPAIGNS_VIEW', 'a0000000-0000-0000-0000-000000000020', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000201', 'CAMPAIGNS_EDIT', 'a0000000-0000-0000-0000-000000000020', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000201', 'REPORTS_VIEW', 'a0000000-0000-0000-0000-000000000020', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000201', 'AI_VIEW', 'a0000000-0000-0000-0000-000000000020', now()),
  -- PetPlanet: read-only
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000202', 'CLIENT_VIEW', 'a0000000-0000-0000-0000-000000000020', now()),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000021', 'b0000000-0000-0000-0000-000000000202', 'CAMPAIGNS_VIEW', 'a0000000-0000-0000-0000-000000000020', now())
ON CONFLICT ON CONSTRAINT uq_ucp_user_client_perm DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 6. META CONNECTIONS
-- ══════════════════════════════════════════════════════════════
INSERT INTO meta_connection (id, agency_id, client_id, ad_account_id, pixel_id, page_id, access_token_enc, token_key_id, status, connected_at, last_sync_at, created_at, updated_at) VALUES
  ('d0000000-0000-0000-0000-000000000101', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'act_ss_1234567', 'px_ss_001', 'page_ss_001', E'\\x00', 'demo-key', 'CONNECTED', now() - interval '5 months', now() - interval '1 hour', now() - interval '5 months', now()),
  ('d0000000-0000-0000-0000-000000000102', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'act_tm_2345678', 'px_tm_001', 'page_tm_001', E'\\x00', 'demo-key', 'CONNECTED', now() - interval '4 months', now() - interval '2 hours', now() - interval '4 months', now()),
  ('d0000000-0000-0000-0000-000000000103', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000103', 'act_gh_3456789', NULL, 'page_gh_001', E'\\x00', 'demo-key', 'DISCONNECTED', now() - interval '2 months', now() - interval '30 days', now() - interval '2 months', now()),
  ('d0000000-0000-0000-0000-000000000201', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'act_fz_4567890', 'px_fz_001', 'page_fz_001', E'\\x00', 'demo-key', 'CONNECTED', now() - interval '2 months', now() - interval '3 hours', now() - interval '2 months', now()),
  ('d0000000-0000-0000-0000-000000000202', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000202', 'act_pp_5678901', 'px_pp_001', 'page_pp_001', E'\\x00', 'demo-key', 'CONNECTED', now() - interval '6 weeks', now() - interval '4 hours', now() - interval '6 weeks', now()),
  ('d0000000-0000-0000-0000-000000000301', 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000301', 'act_ld_6789012', NULL, 'page_ld_001', E'\\x00', 'demo-key', 'CONNECTED', now() - interval '3 weeks', now() - interval '5 hours', now() - interval '3 weeks', now())
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 7. CAMPAIGNS (across agencies — realistic names & statuses)
-- ══════════════════════════════════════════════════════════════

-- BrightWave / StyleShop (4 campaigns)
INSERT INTO campaign (id, agency_id, client_id, platform, meta_campaign_id, name, objective, status, created_by, created_at, updated_at) VALUES
  ('e0000000-0000-0000-0000-000000000101', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'META', 'mc_ss_001', 'StyleShop — Winter Clearance 2025', 'SALES', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000010', now() - interval '120 days', now()),
  ('e0000000-0000-0000-0000-000000000102', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'META', 'mc_ss_002', 'StyleShop — Spring Collection Launch', 'SALES', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000010', now() - interval '75 days', now()),
  ('e0000000-0000-0000-0000-000000000103', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'META', 'mc_ss_003', 'StyleShop — Newsletter Signup', 'LEADS', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000011', now() - interval '60 days', now()),
  ('e0000000-0000-0000-0000-000000000104', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'META', NULL, 'StyleShop — Summer Teaser (Draft)', 'AWARENESS', 'DRAFT', 'a0000000-0000-0000-0000-000000000011', now() - interval '5 days', now())
ON CONFLICT DO NOTHING;

-- BrightWave / TravelMood (3 campaigns)
INSERT INTO campaign (id, agency_id, client_id, platform, meta_campaign_id, name, objective, status, created_by, created_at, updated_at) VALUES
  ('e0000000-0000-0000-0000-000000000111', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'META', 'mc_tm_001', 'TravelMood — Early Bird Summer 2026', 'SALES', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000012', now() - interval '90 days', now()),
  ('e0000000-0000-0000-0000-000000000112', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'META', 'mc_tm_002', 'TravelMood — Weekend Getaway Leads', 'LEADS', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000012', now() - interval '50 days', now()),
  ('e0000000-0000-0000-0000-000000000113', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'META', 'mc_tm_003', 'TravelMood — Brand Awareness', 'AWARENESS', 'PAUSED', 'a0000000-0000-0000-0000-000000000012', now() - interval '30 days', now())
ON CONFLICT DO NOTHING;

-- NexGen / FitZone (3 campaigns)
INSERT INTO campaign (id, agency_id, client_id, platform, meta_campaign_id, name, objective, status, created_by, created_at, updated_at) VALUES
  ('e0000000-0000-0000-0000-000000000201', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'META', 'mc_fz_001', 'FitZone — January Promo', 'SALES', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000020', now() - interval '60 days', now()),
  ('e0000000-0000-0000-0000-000000000202', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'META', 'mc_fz_002', 'FitZone — Free Trial Leads', 'LEADS', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000020', now() - interval '45 days', now()),
  ('e0000000-0000-0000-0000-000000000203', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'META', NULL, 'FitZone — Summer Body (Draft)', 'SALES', 'DRAFT', 'a0000000-0000-0000-0000-000000000021', now() - interval '3 days', now())
ON CONFLICT DO NOTHING;

-- NexGen / PetPlanet (2 campaigns)
INSERT INTO campaign (id, agency_id, client_id, platform, meta_campaign_id, name, objective, status, created_by, created_at, updated_at) VALUES
  ('e0000000-0000-0000-0000-000000000211', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000202', 'META', 'mc_pp_001', 'PetPlanet — Spring Pet Essentials', 'SALES', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000020', now() - interval '40 days', now()),
  ('e0000000-0000-0000-0000-000000000212', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000202', 'META', 'mc_pp_002', 'PetPlanet — Subscription Signup', 'LEADS', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000020', now() - interval '30 days', now())
ON CONFLICT DO NOTHING;

-- AdPulse / LuxDent (2 campaigns)
INSERT INTO campaign (id, agency_id, client_id, platform, meta_campaign_id, name, objective, status, created_by, created_at, updated_at) VALUES
  ('e0000000-0000-0000-0000-000000000301', 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000301', 'META', 'mc_ld_001', 'LuxDent — Free Consultation', 'LEADS', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000030', now() - interval '20 days', now()),
  ('e0000000-0000-0000-0000-000000000302', 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000301', 'META', 'mc_ld_002', 'LuxDent — Teeth Whitening Promo', 'SALES', 'PUBLISHED', 'a0000000-0000-0000-0000-000000000030', now() - interval '14 days', now())
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 8. INSIGHT_DAILY — 90+ days for main clients
--    Generates realistic data with seasonal trends
-- ══════════════════════════════════════════════════════════════

-- StyleShop — Winter Clearance (90 days, high spend declining over time)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000101', d::date,
  round((random() * 30 + 40 + (120 - (current_date - d::date)) * 0.3)::numeric, 2),
  (random() * 6000 + 3000 + (120 - (current_date - d::date)) * 20)::bigint,
  (random() * 150 + 60)::bigint,
  round((random() * 1.5 + 1.5)::numeric, 6),
  round((random() * 0.3 + 0.25)::numeric, 6),
  round((random() * 4 + 6)::numeric, 6),
  round((random() * 6 + 3)::numeric, 6),
  round((random() * 350 + 100)::numeric, 2),
  round((random() * 3 + 2)::numeric, 6),
  round((random() * 1.2 + 1)::numeric, 6),
  (random() * 3000 + 2000)::bigint, now()
FROM generate_series(current_date - interval '120 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- StyleShop — Spring Collection (75 days, growing spend)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000102', d::date,
  round((random() * 25 + 50 + (d::date - (current_date - interval '75 days')::date) * 0.4)::numeric, 2),
  (random() * 7000 + 4000)::bigint,
  (random() * 180 + 80)::bigint,
  round((random() * 1.8 + 1.2)::numeric, 6),
  round((random() * 0.25 + 0.2)::numeric, 6),
  round((random() * 3.5 + 5)::numeric, 6),
  round((random() * 8 + 4)::numeric, 6),
  round((random() * 500 + 150)::numeric, 2),
  round((random() * 4 + 2.5)::numeric, 6),
  round((random() * 1 + 1)::numeric, 6),
  (random() * 4000 + 3000)::bigint, now()
FROM generate_series(current_date - interval '75 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- StyleShop — Newsletter (60 days, low spend, high conversion rate)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000103', d::date,
  round((random() * 15 + 20)::numeric, 2),
  (random() * 4000 + 2000)::bigint,
  (random() * 100 + 40)::bigint,
  round((random() * 2.5 + 1.5)::numeric, 6),
  round((random() * 0.2 + 0.15)::numeric, 6),
  round((random() * 3 + 4)::numeric, 6),
  round((random() * 12 + 5)::numeric, 6),
  round((random() * 50 + 10)::numeric, 2),
  round((random() * 1.5 + 0.5)::numeric, 6),
  round((random() * 0.8 + 1)::numeric, 6),
  (random() * 3500 + 1800)::bigint, now()
FROM generate_series(current_date - interval '60 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- TravelMood — Early Bird (90 days)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000111', d::date,
  round((random() * 50 + 70)::numeric, 2),
  (random() * 10000 + 5000)::bigint,
  (random() * 200 + 100)::bigint,
  round((random() * 1.5 + 1)::numeric, 6),
  round((random() * 0.4 + 0.3)::numeric, 6),
  round((random() * 5 + 7)::numeric, 6),
  round((random() * 4 + 1)::numeric, 6),
  round((random() * 800 + 200)::numeric, 2),
  round((random() * 5 + 2)::numeric, 6),
  round((random() * 1.5 + 1)::numeric, 6),
  (random() * 5000 + 3000)::bigint, now()
FROM generate_series(current_date - interval '90 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- FitZone — January Promo (60 days)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000201', d::date,
  round((random() * 30 + 35)::numeric, 2),
  (random() * 5000 + 2500)::bigint,
  (random() * 120 + 50)::bigint,
  round((random() * 2 + 1.5)::numeric, 6),
  round((random() * 0.35 + 0.2)::numeric, 6),
  round((random() * 4 + 5)::numeric, 6),
  round((random() * 5 + 2)::numeric, 6),
  round((random() * 200 + 80)::numeric, 2),
  round((random() * 4 + 1.5)::numeric, 6),
  round((random() * 1.3 + 1)::numeric, 6),
  (random() * 2500 + 1500)::bigint, now()
FROM generate_series(current_date - interval '60 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- FitZone — Free Trial Leads (45 days)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000202', d::date,
  round((random() * 20 + 25)::numeric, 2),
  (random() * 4000 + 2000)::bigint,
  (random() * 100 + 40)::bigint,
  round((random() * 2.5 + 1.5)::numeric, 6),
  round((random() * 0.25 + 0.18)::numeric, 6),
  round((random() * 3 + 5)::numeric, 6),
  round((random() * 8 + 3)::numeric, 6),
  round((random() * 40 + 10)::numeric, 2),
  round((random() * 1 + 0.3)::numeric, 6),
  round((random() * 1 + 1)::numeric, 6),
  (random() * 3000 + 1500)::bigint, now()
FROM generate_series(current_date - interval '45 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- PetPlanet — Spring Essentials (40 days)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000202', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000211', d::date,
  round((random() * 25 + 30)::numeric, 2),
  (random() * 5500 + 2500)::bigint,
  (random() * 130 + 50)::bigint,
  round((random() * 2 + 1.3)::numeric, 6),
  round((random() * 0.3 + 0.2)::numeric, 6),
  round((random() * 3.5 + 5)::numeric, 6),
  round((random() * 7 + 3)::numeric, 6),
  round((random() * 300 + 90)::numeric, 2),
  round((random() * 5 + 2)::numeric, 6),
  round((random() * 1.2 + 1)::numeric, 6),
  (random() * 3000 + 2000)::bigint, now()
FROM generate_series(current_date - interval '40 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- LuxDent — Free Consultation (20 days, high CPC/low volume - medical niche)
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000301', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000301', d::date,
  round((random() * 20 + 40)::numeric, 2),
  (random() * 2500 + 1000)::bigint,
  (random() * 40 + 15)::bigint,
  round((random() * 1 + 0.8)::numeric, 6),
  round((random() * 1.2 + 0.8)::numeric, 6),
  round((random() * 8 + 12)::numeric, 6),
  round((random() * 3 + 1)::numeric, 6),
  round((random() * 600 + 200)::numeric, 2),
  round((random() * 6 + 3)::numeric, 6),
  round((random() * 1 + 1)::numeric, 6),
  (random() * 1500 + 800)::bigint, now()
FROM generate_series(current_date - interval '20 days', current_date - interval '1 day', '1 day') d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 9. META SYNC JOBS
-- ══════════════════════════════════════════════════════════════
INSERT INTO meta_sync_job (id, agency_id, client_id, job_type, job_status, idempotency_key, requested_at, started_at, finished_at, stats_json) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'INITIAL', 'SUCCESS', 'init-ss-001', now() - interval '5 months', now() - interval '5 months', now() - interval '5 months' + interval '3 minutes', '{"campaigns":4,"adsets":6,"ads":10,"insights_days":90}'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'DAILY', 'SUCCESS', 'daily-ss-today', now() - interval '1 hour', now() - interval '1 hour', now() - interval '55 minutes', '{"campaigns":4,"adsets":6,"ads":10,"insights_days":7}'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'INITIAL', 'SUCCESS', 'init-fz-001', now() - interval '2 months', now() - interval '2 months', now() - interval '2 months' + interval '2 minutes', '{"campaigns":3,"adsets":4,"ads":6,"insights_days":60}'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'DAILY', 'FAILED', 'daily-fz-fail', now() - interval '6 hours', now() - interval '6 hours', now() - interval '6 hours' + interval '30 seconds', NULL),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000301', 'INITIAL', 'SUCCESS', 'init-ld-001', now() - interval '3 weeks', now() - interval '3 weeks', now() - interval '3 weeks' + interval '1 minute', '{"campaigns":2,"adsets":3,"ads":5,"insights_days":20}')
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 10. AI SUGGESTIONS
-- ══════════════════════════════════════════════════════════════
INSERT INTO ai_suggestion (id, agency_id, client_id, scope_type, scope_id, suggestion_type, payload_json, rationale, confidence, risk_level, status, cooldown_until, created_by, created_at, reviewed_by, reviewed_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000101', 'BUDGET_ADJUST',
   '{"current_budget":50,"proposed_budget":60,"change_percent":20}',
   'Winter Clearance campaign has ROAS of 3.8 over the last 14 days with stable CPA. Budget increase of 20% is recommended to capture remaining demand before season ends.',
   0.850, 'MEDIUM', 'PENDING', NULL, 'AI', now() - interval '4 hours', NULL, NULL),

  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000102', 'DIAGNOSTIC',
   '{"alert":"frequency_rising","current_frequency":2.6,"threshold":2.0}',
   'Spring Collection campaign frequency is 2.6 (above 2.0 threshold). CTR dropped 22% in last 5 days suggesting audience fatigue. Consider refreshing creatives or expanding targeting.',
   0.920, 'LOW', 'APPROVED', NULL, 'AI', now() - interval '2 days', 'a0000000-0000-0000-0000-000000000010', now() - interval '1 day'),

  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000111', 'BUDGET_ADJUST',
   '{"current_budget":80,"proposed_budget":95,"change_percent":18.75}',
   'TravelMood Early Bird campaign has consistently high ROAS of 4.2+ and CPA well within target. Peak booking season is approaching. 18.75% budget increase recommended.',
   0.880, 'MEDIUM', 'PENDING', NULL, 'AI', now() - interval '6 hours', NULL, NULL),

  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000201', 'PAUSE',
   '{"entity":"campaign","action":"pause","reason":"budget_exhaustion_low_roas"}',
   'FitZone January Promo ROAS dropped to 1.2 in last 7 days while CPA increased 40%. Campaign may be past peak performance. Recommend pausing to preserve budget.',
   0.780, 'HIGH', 'REJECTED', NULL, 'AI', now() - interval '5 days', 'a0000000-0000-0000-0000-000000000020', now() - interval '4 days'),

  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000202', 'CAMPAIGN', 'e0000000-0000-0000-0000-000000000211', 'CREATIVE_TEST',
   '{"suggestion":"test_video_creative","current_format":"image","proposed_format":"video"}',
   'PetPlanet image ads have CTR of 1.8% while industry video average is 2.5%. Testing video creative could improve engagement. Current spend allows A/B testing.',
   0.720, 'LOW', 'PENDING', NULL, 'AI', now() - interval '2 hours', NULL, NULL)
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 11. REPORTS
-- ══════════════════════════════════════════════════════════════
INSERT INTO report (id, agency_id, client_id, report_type, period_start, period_end, status, html_content, pdf_s3_key, created_by, approved_by, created_at, approved_at, sent_at) VALUES
  -- StyleShop: Jan sent, Feb approved, Mar draft
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'MONTHLY', '2025-12-01', '2025-12-31', 'SENT',
   '<html><body><h1>Performance Report</h1><h2>StyleShop.bg — December 2025</h2><p>Spend: 2,450 BGN | Impressions: 185K | Clicks: 4,200 | CTR: 2.27% | Conversions: 312 | ROAS: 3.4</p><p>Winter season drove strong performance with clearance campaigns performing above target.</p></body></html>',
   'reports/styleshop/2025-12.pdf', 'a0000000-0000-0000-0000-000000000010', 'a0000000-0000-0000-0000-000000000010', now() - interval '65 days', now() - interval '64 days', now() - interval '63 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'MONTHLY', '2026-01-01', '2026-01-31', 'SENT',
   '<html><body><h1>Performance Report</h1><h2>StyleShop.bg — January 2026</h2><p>Spend: 2,780 BGN | Impressions: 210K | Clicks: 4,800 | CTR: 2.29% | Conversions: 345 | ROAS: 3.6</p><p>January clearance extended strong December performance. Spring collection prep started.</p></body></html>',
   'reports/styleshop/2026-01.pdf', 'a0000000-0000-0000-0000-000000000010', 'a0000000-0000-0000-0000-000000000010', now() - interval '35 days', now() - interval '34 days', now() - interval '33 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'MONTHLY', '2026-02-01', '2026-02-28', 'APPROVED',
   '<html><body><h1>Performance Report</h1><h2>StyleShop.bg — February 2026</h2><p>Spend: 3,120 BGN | Impressions: 240K | Clicks: 5,400 | CTR: 2.25% | Conversions: 398 | ROAS: 3.8</p><p>Spring Collection launch exceeded expectations. Newsletter campaign contributed strong lead gen numbers.</p></body></html>',
   NULL, 'a0000000-0000-0000-0000-000000000010', 'a0000000-0000-0000-0000-000000000010', now() - interval '5 days', now() - interval '3 days', NULL),

  -- TravelMood: Jan sent
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'MONTHLY', '2026-01-01', '2026-01-31', 'SENT',
   '<html><body><h1>Performance Report</h1><h2>TravelMood — January 2026</h2><p>Spend: 3,500 EUR | Impressions: 310K | Clicks: 5,100 | CTR: 1.65% | Bookings: 42 | ROAS: 4.1</p><p>Early Bird Summer campaign delivered excellent booking numbers. CPC remains competitive.</p></body></html>',
   'reports/travelmood/2026-01.pdf', 'a0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-000000000010', now() - interval '33 days', now() - interval '32 days', now() - interval '31 days'),

  -- FitZone: Feb draft
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000201', 'MONTHLY', '2026-02-01', '2026-02-28', 'DRAFT',
   '<html><body><h1>Performance Report</h1><h2>FitZone Gym — February 2026</h2><p>Spend: 1,890 BGN | Impressions: 145K | Clicks: 3,200 | CTR: 2.21% | Signups: 178 | CPA: 10.62 BGN</p><p>January promo continued momentum. Free trial leads campaign showed strong intent signals.</p></body></html>',
   NULL, 'a0000000-0000-0000-0000-000000000020', NULL, now() - interval '4 days', NULL, NULL)
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 12. FEEDBACK
-- ══════════════════════════════════════════════════════════════
INSERT INTO feedback (id, agency_id, client_id, source_type, source_id, rating, comment, created_by, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000101', 'REPORT', (SELECT id FROM report WHERE agency_id = 'a0000000-0000-0000-0000-000000000001' AND client_id = 'b0000000-0000-0000-0000-000000000101' AND status = 'SENT' LIMIT 1), 5, 'Отличен отчет, много ясен и подробен!', 'a0000000-0000-0000-0000-000000000050', now() - interval '60 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000102', 'REPORT', (SELECT id FROM report WHERE agency_id = 'a0000000-0000-0000-0000-000000000001' AND client_id = 'b0000000-0000-0000-0000-000000000102' LIMIT 1), 4, 'Good overview, would like more detail on booking sources next time.', 'a0000000-0000-0000-0000-000000000010', now() - interval '28 days')
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- DONE! Summary of test accounts:
-- ══════════════════════════════════════════════════════════════
-- All passwords: admin123
--
-- OWNER:          owner@local
--
-- BRIGHTWAVE:     maria@brightwave.bg   (AGENCY_ADMIN)
--                 georgi@brightwave.bg  (AGENCY_USER — StyleShop editor, TravelMood read-only)
--                 elena@brightwave.bg   (AGENCY_USER — TravelMood full, GreenHome read-only)
--                 client@styleshop.bg   (CLIENT_USER)
--
-- NEXGEN:         ivan@nexgen.bg        (AGENCY_ADMIN)
--                 petya@nexgen.bg       (AGENCY_USER — FitZone editor, PetPlanet read-only)
--                 client@fitzone.bg     (CLIENT_USER)
--
-- ADPULSE:        stefan@adpulse.bg     (AGENCY_ADMIN)
--                 ana@adpulse.bg        (AGENCY_USER)
-- ══════════════════════════════════════════════════════════════