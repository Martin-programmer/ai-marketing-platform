-- =============================================================
-- Seed data for local development. Safe to run multiple times.
-- All INSERTs use ON CONFLICT DO NOTHING for idempotency.
-- =============================================================

-- ──────── 1. Agency ────────
INSERT INTO agency (id, name, status, plan_code, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo Agency', 'ACTIVE', 'PRO', now(), now())
ON CONFLICT DO NOTHING;

-- ──────── 2. Users ────────
INSERT INTO user_account (id, agency_id, cognito_sub, email, role, status, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'local-sub-agency-admin', 'agency_admin@local', 'AGENCY_ADMIN', 'ACTIVE', now(), now()),
  ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001', 'local-sub-agency-user',  'agency_user@local',  'AGENCY_USER', 'ACTIVE', now(), now()),
  ('00000000-0000-0000-0000-000000000020', NULL,                                   'local-sub-owner-admin',  'owner_admin@local',  'OWNER_ADMIN', 'ACTIVE', now(), now())
ON CONFLICT DO NOTHING;

-- ──────── 3. Clients ────────
INSERT INTO client (id, agency_id, name, industry, status, timezone, currency, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000001', 'Demo Client', 'ECOM', 'ACTIVE', 'Europe/Sofia', 'BGN', now(), now()),
  ('00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000001', 'FitFood.bg', 'ECOM', 'ACTIVE', 'Europe/Sofia', 'BGN', now(), now())
ON CONFLICT DO NOTHING;

-- ──────── 4. Client Profiles ────────
INSERT INTO client_profile (id, agency_id, client_id, website, profile_json, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'https://democlient.bg',
   '{"usp": "Premium quality products at affordable prices", "audiences": ["25-45 women", "parents"], "tone": "friendly and professional", "offers": ["Free shipping over 50 BGN", "10% first order"], "competitors": ["competitor1.bg", "competitor2.bg"], "restrictions": "No health claims"}',
   now(), now()),
  ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'https://fitfood.bg',
   '{"usp": "Healthy meal prep delivery in Sofia", "audiences": ["fitness enthusiasts", "busy professionals 25-40"], "tone": "energetic and motivational", "offers": ["First box -20%", "Subscribe & save 15%"], "competitors": ["healthbox.bg", "fitprep.bg"], "restrictions": "No before/after body images"}',
   now(), now())
ON CONFLICT DO NOTHING;

-- ──────── 5. Meta Connections ────────
INSERT INTO meta_connection (id, agency_id, client_id, ad_account_id, pixel_id, page_id, access_token_enc, token_key_id, status, connected_at, last_sync_at, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'act_1234567890', 'px_9876543210', 'page_111222333', E'\\x00', 'demo-key', 'CONNECTED', now() - interval '30 days', now() - interval '1 hour', now() - interval '30 days', now()),
  ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'act_0987654321', 'px_1234509876', 'page_444555666', E'\\x00', 'demo-key', 'CONNECTED', now() - interval '20 days', now() - interval '2 hours', now() - interval '20 days', now())
ON CONFLICT DO NOTHING;

-- ──────── 6. Meta Sync Jobs ────────
INSERT INTO meta_sync_job (id, agency_id, client_id, job_type, job_status, idempotency_key, requested_at, started_at, finished_at, stats_json)
VALUES
  ('00000000-0000-0000-0000-000000000311', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'DAILY', 'SUCCESS', 'sync-demo-100-daily-1', now() - interval '2 hours', now() - interval '2 hours', now() - interval '1 hour', '{"campaigns": 3, "adsets": 6, "ads": 12, "insights_days": 7}'),
  ('00000000-0000-0000-0000-000000000312', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'DAILY', 'SUCCESS', 'sync-demo-200-daily-1', now() - interval '3 hours', now() - interval '3 hours', now() - interval '2 hours', '{"campaigns": 2, "adsets": 4, "ads": 8, "insights_days": 7}')
ON CONFLICT DO NOTHING;

-- ──────── 7. Campaigns — Demo Client (3) ────────
INSERT INTO campaign (id, agency_id, client_id, platform, meta_campaign_id, name, objective, status, created_by, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'META', 'mc_100001', 'Demo - Spring Sale 2026', 'SALES', 'PUBLISHED', '00000000-0000-0000-0000-000000000010', now() - interval '25 days', now()),
  ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'META', 'mc_100002', 'Demo - Lead Gen Newsletter', 'LEADS', 'PUBLISHED', '00000000-0000-0000-0000-000000000010', now() - interval '20 days', now()),
  ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'META', NULL, 'Demo - Summer Awareness (Draft)', 'AWARENESS', 'DRAFT', '00000000-0000-0000-0000-000000000010', now() - interval '2 days', now())
ON CONFLICT DO NOTHING;

-- ──────── 8. Campaigns — FitFood.bg (2) ────────
INSERT INTO campaign (id, agency_id, client_id, platform, meta_campaign_id, name, objective, status, created_by, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000411', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'META', 'mc_200001', 'FitFood - Meal Plans Promo', 'SALES', 'PUBLISHED', '00000000-0000-0000-0000-000000000010', now() - interval '15 days', now()),
  ('00000000-0000-0000-0000-000000000412', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'META', 'mc_200002', 'FitFood - New Subscribers', 'LEADS', 'PAUSED', '00000000-0000-0000-0000-000000000010', now() - interval '10 days', now())
ON CONFLICT DO NOTHING;

-- ──────── 9. Adsets (2 per published campaign) ────────
INSERT INTO adset (id, agency_id, client_id, campaign_id, meta_adset_id, name, daily_budget, targeting_json, status, created_at, updated_at)
VALUES
  -- Demo Spring Sale adsets
  ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000401', 'mas_101', 'Spring Sale - Broad 25-45', 50.00, '{"age_min": 25, "age_max": 45, "genders": ["female"], "interests": ["shopping", "fashion"]}', 'PUBLISHED', now() - interval '25 days', now()),
  ('00000000-0000-0000-0000-000000000502', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000401', 'mas_102', 'Spring Sale - Retargeting', 30.00, '{"custom_audience": "website_visitors_30d", "exclude": "purchasers_30d"}', 'PUBLISHED', now() - interval '25 days', now()),
  -- Demo Lead Gen adsets
  ('00000000-0000-0000-0000-000000000503', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000402', 'mas_103', 'Newsletter - Interest Based', 25.00, '{"age_min": 20, "age_max": 55, "interests": ["newsletters", "deals"]}', 'PUBLISHED', now() - interval '20 days', now()),
  ('00000000-0000-0000-0000-000000000504', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000402', 'mas_104', 'Newsletter - Lookalike', 35.00, '{"lookalike_source": "subscribers", "lookalike_percent": 2}', 'PUBLISHED', now() - interval '20 days', now()),
  -- FitFood Meal Plans adsets
  ('00000000-0000-0000-0000-000000000511', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000411', 'mas_201', 'Meal Plans - Fitness Enthusiasts', 40.00, '{"age_min": 22, "age_max": 40, "interests": ["fitness", "healthy eating", "gym"]}', 'PUBLISHED', now() - interval '15 days', now()),
  ('00000000-0000-0000-0000-000000000512', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000411', 'mas_202', 'Meal Plans - Busy Professionals', 35.00, '{"age_min": 28, "age_max": 45, "interests": ["meal prep", "time saving", "office lunch"]}', 'PUBLISHED', now() - interval '15 days', now())
ON CONFLICT DO NOTHING;

-- ──────── 10. Ads (2 per adset) ────────
INSERT INTO ad (id, agency_id, client_id, adset_id, meta_ad_id, name, status, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000501', 'mad_1001', 'Spring Sale - Image Ad A', 'PUBLISHED', now() - interval '25 days', now()),
  ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000501', 'mad_1002', 'Spring Sale - Video Ad B', 'PUBLISHED', now() - interval '25 days', now()),
  ('00000000-0000-0000-0000-000000000603', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000502', 'mad_1003', 'Retargeting - Carousel', 'PUBLISHED', now() - interval '25 days', now()),
  ('00000000-0000-0000-0000-000000000604', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000502', 'mad_1004', 'Retargeting - Dynamic', 'PAUSED', now() - interval '20 days', now()),
  ('00000000-0000-0000-0000-000000000605', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000503', 'mad_1005', 'Newsletter - Lead Form', 'PUBLISHED', now() - interval '20 days', now()),
  ('00000000-0000-0000-0000-000000000606', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000504', 'mad_1006', 'Newsletter - Lookalike Video', 'PUBLISHED', now() - interval '20 days', now()),
  ('00000000-0000-0000-0000-000000000611', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000511', 'mad_2001', 'FitFood - Hero Image', 'PUBLISHED', now() - interval '15 days', now()),
  ('00000000-0000-0000-0000-000000000612', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000511', 'mad_2002', 'FitFood - UGC Video', 'PUBLISHED', now() - interval '15 days', now()),
  ('00000000-0000-0000-0000-000000000613', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000512', 'mad_2003', 'FitFood - Office Lunch Ad', 'PUBLISHED', now() - interval '15 days', now()),
  ('00000000-0000-0000-0000-000000000614', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000512', 'mad_2004', 'FitFood - Testimonial', 'PUBLISHED', now() - interval '10 days', now())
ON CONFLICT DO NOTHING;

-- ──────── 11. Creative Assets (4 per client) ────────
INSERT INTO creative_asset (id, agency_id, client_id, asset_type, s3_bucket, s3_key, original_filename, mime_type, size_bytes, width_px, height_px, checksum_sha256, status, created_by, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'IMAGE', 'demo-bucket', 'agencies/demo/clients/100/spring-sale-banner.jpg', 'spring-sale-banner.jpg', 'image/jpeg', 245000, 1080, 1080, 'sha256_demo_1', 'READY', '00000000-0000-0000-0000-000000000010', now() - interval '28 days', now()),
  ('00000000-0000-0000-0000-000000000702', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'VIDEO', 'demo-bucket', 'agencies/demo/clients/100/spring-sale-video.mp4', 'spring-sale-video.mp4', 'video/mp4', 5200000, 1080, 1920, 'sha256_demo_2', 'READY', '00000000-0000-0000-0000-000000000010', now() - interval '27 days', now()),
  ('00000000-0000-0000-0000-000000000703', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'IMAGE', 'demo-bucket', 'agencies/demo/clients/100/newsletter-cta.png', 'newsletter-cta.png', 'image/png', 180000, 1200, 628, 'sha256_demo_3', 'READY', '00000000-0000-0000-0000-000000000010', now() - interval '22 days', now()),
  ('00000000-0000-0000-0000-000000000704', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'IMAGE', 'demo-bucket', 'agencies/demo/clients/100/carousel-product.jpg', 'carousel-product.jpg', 'image/jpeg', 320000, 1080, 1080, 'sha256_demo_4', 'ANALYZING', '00000000-0000-0000-0000-000000000010', now() - interval '3 days', now()),
  ('00000000-0000-0000-0000-000000000711', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'IMAGE', 'demo-bucket', 'agencies/demo/clients/200/hero-meal-box.jpg', 'hero-meal-box.jpg', 'image/jpeg', 410000, 1080, 1080, 'sha256_demo_5', 'READY', '00000000-0000-0000-0000-000000000010', now() - interval '18 days', now()),
  ('00000000-0000-0000-0000-000000000712', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'VIDEO', 'demo-bucket', 'agencies/demo/clients/200/ugc-unboxing.mp4', 'ugc-unboxing.mp4', 'video/mp4', 8900000, 1080, 1920, 'sha256_demo_6', 'READY', '00000000-0000-0000-0000-000000000010', now() - interval '17 days', now()),
  ('00000000-0000-0000-0000-000000000713', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'IMAGE', 'demo-bucket', 'agencies/demo/clients/200/office-lunch-flat.jpg', 'office-lunch-flat.jpg', 'image/jpeg', 290000, 1200, 628, 'sha256_demo_7', 'READY', '00000000-0000-0000-0000-000000000010', now() - interval '16 days', now()),
  ('00000000-0000-0000-0000-000000000714', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'VIDEO', 'demo-bucket', 'agencies/demo/clients/200/testimonial-clip.mp4', 'testimonial-clip.mp4', 'video/mp4', 12300000, 1080, 1080, 'sha256_demo_8', 'READY', '00000000-0000-0000-0000-000000000010', now() - interval '12 days', now())
ON CONFLICT DO NOTHING;

-- ──────── 12. Creative Packages (2 per client) ────────
INSERT INTO creative_package (id, agency_id, client_id, name, objective, status, notes, created_by, approved_by, created_at, approved_at)
VALUES
  ('00000000-0000-0000-0000-000000000751', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'Spring Sale Package A', 'SALES', 'APPROVED', 'Main creative set for spring campaign', '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000010', now() - interval '26 days', now() - interval '25 days'),
  ('00000000-0000-0000-0000-000000000752', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'Newsletter Creative Set', 'LEADS', 'IN_REVIEW', 'Lead gen focused creatives', '00000000-0000-0000-0000-000000000020', NULL, now() - interval '5 days', NULL),
  ('00000000-0000-0000-0000-000000000761', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'FitFood Launch Bundle', 'SALES', 'APPROVED', 'Hero + UGC combination', '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000010', now() - interval '16 days', now() - interval '15 days'),
  ('00000000-0000-0000-0000-000000000762', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'FitFood Office Segment', 'SALES', 'DRAFT', 'Targeting office workers', '00000000-0000-0000-0000-000000000020', NULL, now() - interval '3 days', NULL)
ON CONFLICT DO NOTHING;

-- ──────── 13. Insight Daily — Demo Client: Spring Sale (30 days) ────────
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT
  gen_random_uuid(),
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000100',
  'CAMPAIGN',
  '00000000-0000-0000-0000-000000000401',
  d::date,
  round((random() * 40 + 60)::numeric, 2),
  (random() * 8000 + 4000)::bigint,
  (random() * 200 + 80)::bigint,
  round((random() * 2 + 1)::numeric, 6),
  round((random() * 0.5 + 0.2)::numeric, 6),
  round((random() * 5 + 5)::numeric, 6),
  round((random() * 8 + 2)::numeric, 6),
  round((random() * 400 + 100)::numeric, 2),
  round((random() * 4 + 1)::numeric, 6),
  round((random() * 1.5 + 1)::numeric, 6),
  (random() * 3000 + 2000)::bigint,
  now()
FROM generate_series(current_date - interval '30 days', current_date - interval '1 day', interval '1 day') AS d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- ──────── 14. Insight Daily — Demo Client: Lead Gen (20 days) ────────
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT
  gen_random_uuid(),
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000100',
  'CAMPAIGN',
  '00000000-0000-0000-0000-000000000402',
  d::date,
  round((random() * 30 + 40)::numeric, 2),
  (random() * 6000 + 3000)::bigint,
  (random() * 150 + 50)::bigint,
  round((random() * 1.5 + 0.8)::numeric, 6),
  round((random() * 0.4 + 0.3)::numeric, 6),
  round((random() * 4 + 4)::numeric, 6),
  round((random() * 5 + 1)::numeric, 6),
  round((random() * 100 + 20)::numeric, 2),
  round((random() * 2 + 0.5)::numeric, 6),
  round((random() * 1.2 + 1)::numeric, 6),
  (random() * 2500 + 1500)::bigint,
  now()
FROM generate_series(current_date - interval '20 days', current_date - interval '1 day', interval '1 day') AS d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- ──────── 15. Insight Daily — FitFood: Meal Plans (15 days) ────────
INSERT INTO insight_daily (id, agency_id, client_id, entity_type, entity_id, date, spend, impressions, clicks, ctr, cpc, cpm, conversions, conversion_value, roas, frequency, reach, created_at)
SELECT
  gen_random_uuid(),
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000200',
  'CAMPAIGN',
  '00000000-0000-0000-0000-000000000411',
  d::date,
  round((random() * 30 + 30)::numeric, 2),
  (random() * 5000 + 2000)::bigint,
  (random() * 120 + 40)::bigint,
  round((random() * 2 + 1.2)::numeric, 6),
  round((random() * 0.3 + 0.25)::numeric, 6),
  round((random() * 3 + 6)::numeric, 6),
  round((random() * 6 + 2)::numeric, 6),
  round((random() * 300 + 80)::numeric, 2),
  round((random() * 5 + 2)::numeric, 6),
  round((random() * 1 + 1)::numeric, 6),
  (random() * 2000 + 1000)::bigint,
  now()
FROM generate_series(current_date - interval '15 days', current_date - interval '1 day', interval '1 day') AS d
ON CONFLICT (entity_type, entity_id, date) DO NOTHING;

-- ──────── 16. AI Suggestions (mixed statuses & types) ────────
INSERT INTO ai_suggestion (id, agency_id, client_id, scope_type, scope_id, suggestion_type, payload_json, rationale, confidence, risk_level, status, cooldown_until, created_by, created_at, reviewed_by, reviewed_at)
VALUES
  ('00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'ADSET', '00000000-0000-0000-0000-000000000501', 'BUDGET_ADJUST',
   '{"current_budget": 50.00, "proposed_budget": 55.00, "change_percent": 10}',
   'CTR is stable at 2.3% and CPA decreased 15% over the last 7 days. Increasing budget by 10% to capture more conversions while maintaining efficiency.', 0.820, 'MEDIUM', 'PENDING', NULL, 'AI', now() - interval '6 hours', NULL, NULL),
  ('00000000-0000-0000-0000-000000000802', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'AD', '00000000-0000-0000-0000-000000000604', 'PAUSE',
   '{"entity": "ad", "action": "pause", "reason": "high_spend_zero_conversions"}',
   'This ad has spent 120 BGN in the last 7 days with 0 conversions. CTR is 0.4% which is well below the adset average of 1.8%. Recommend pausing to reallocate budget.', 0.910, 'MEDIUM', 'APPROVED', NULL, 'AI', now() - interval '2 days', '00000000-0000-0000-0000-000000000010', now() - interval '1 day'),
  ('00000000-0000-0000-0000-000000000803', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'CAMPAIGN', '00000000-0000-0000-0000-000000000401', 'DIAGNOSTIC',
   '{"alert": "frequency_rising", "current_frequency": 2.4, "threshold": 2.0}',
   'Campaign frequency has risen to 2.4 (above 2.0 threshold) in the last 5 days while CTR dropped 18%. This indicates audience fatigue. Consider refreshing creatives or expanding audiences.', 0.880, 'LOW', 'PENDING', NULL, 'AI', now() - interval '12 hours', NULL, NULL),
  ('00000000-0000-0000-0000-000000000804', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'ADSET', '00000000-0000-0000-0000-000000000503', 'CREATIVE_TEST',
   '{"package_id": "00000000-0000-0000-0000-000000000752", "test_duration_days": 5}',
   'The Newsletter adset has been running the same creative for 18 days. CTR has declined 22% from peak. Recommend testing the new Newsletter Creative Set package.', 0.750, 'LOW', 'REJECTED', NULL, 'AI', now() - interval '5 days', '00000000-0000-0000-0000-000000000010', now() - interval '4 days'),
  ('00000000-0000-0000-0000-000000000805', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'ADSET', '00000000-0000-0000-0000-000000000502', 'BUDGET_ADJUST',
   '{"current_budget": 30.00, "proposed_budget": 33.00, "change_percent": 10}',
   'Retargeting adset ROAS is 4.2 over the last 14 days, consistently above target of 3.0. Budget increase of 10% recommended.', 0.850, 'MEDIUM', 'APPLIED', now() - interval '4 days', 'AI', now() - interval '8 days', '00000000-0000-0000-0000-000000000010', now() - interval '7 days'),
  -- FitFood suggestions
  ('00000000-0000-0000-0000-000000000811', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'CAMPAIGN', '00000000-0000-0000-0000-000000000412', 'ENABLE',
   '{"entity": "campaign", "action": "enable", "reason": "issue_resolved"}',
   'The New Subscribers campaign was paused due to ad rejection. The rejected ad has been fixed and re-approved by Meta. Recommend re-enabling the campaign.', 0.900, 'LOW', 'PENDING', NULL, 'AI', now() - interval '4 hours', NULL, NULL),
  ('00000000-0000-0000-0000-000000000812', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'ADSET', '00000000-0000-0000-0000-000000000511', 'BUDGET_ADJUST',
   '{"current_budget": 40.00, "proposed_budget": 44.00, "change_percent": 10}',
   'Fitness Enthusiasts adset showing strong performance: CPA 8.50 BGN (target: 12 BGN), ROAS 3.8. Stable for 10+ days. Budget increase recommended.', 0.870, 'MEDIUM', 'PENDING', NULL, 'AI', now() - interval '3 hours', NULL, NULL)
ON CONFLICT DO NOTHING;

-- ──────── 17. AI Action Log (for APPLIED suggestion) ────────
INSERT INTO ai_action_log (id, agency_id, client_id, suggestion_id, executed_by, meta_request_json, meta_response_json, success, created_at)
VALUES
  ('00000000-0000-0000-0000-000000000851', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000805', 'AI',
   '{"endpoint": "adsets/mas_102", "method": "POST", "payload": {"daily_budget": 3300}}',
   '{"success": true, "updated_budget": 3300}',
   true, now() - interval '7 days')
ON CONFLICT DO NOTHING;

-- ──────── 18. Reports (1 sent, 1 draft/approved per client) ────────
INSERT INTO report (id, agency_id, client_id, report_type, period_start, period_end, status, html_content, pdf_s3_key, created_by, approved_by, created_at, approved_at, sent_at)
VALUES
  ('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'MONTHLY', '2026-01-01', '2026-01-31', 'SENT', '<h1>January 2026 Report - Demo Client</h1><p>Total spend: 2,340 BGN. Conversions: 187. ROAS: 3.2</p>', 'reports/demo/2026-01.pdf', '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000010', now() - interval '30 days', now() - interval '29 days', now() - interval '28 days'),
  ('00000000-0000-0000-0000-000000000902', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'MONTHLY', '2026-02-01', '2026-02-28', 'DRAFT', '<h1>February 2026 Report - Demo Client</h1><p>Total spend: 2,580 BGN. Conversions: 210. ROAS: 3.5</p>', NULL, '00000000-0000-0000-0000-000000000010', NULL, now() - interval '2 days', NULL, NULL),
  ('00000000-0000-0000-0000-000000000911', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000200', 'MONTHLY', '2026-02-01', '2026-02-28', 'APPROVED', '<h1>February 2026 Report - FitFood.bg</h1><p>Total spend: 1,050 BGN. Conversions: 89. ROAS: 4.1</p>', 'reports/fitfood/2026-02.pdf', '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000010', now() - interval '3 days', now() - interval '1 day', NULL)
ON CONFLICT DO NOTHING;

-- ──────── 19. Feedback ────────
INSERT INTO feedback (id, agency_id, client_id, source_type, source_id, rating, comment, created_by, created_at)
VALUES
  ('00000000-0000-0000-0000-000000000951', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'REPORT', '00000000-0000-0000-0000-000000000901', 5, 'Great report, very clear and detailed!', '00000000-0000-0000-0000-000000000010', now() - interval '27 days'),
  ('00000000-0000-0000-0000-000000000952', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', 'SUGGESTION', '00000000-0000-0000-0000-000000000805', 4, 'Good suggestion, the budget increase worked well', '00000000-0000-0000-0000-000000000010', now() - interval '5 days')
ON CONFLICT DO NOTHING;
