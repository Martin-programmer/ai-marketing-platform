-- ============================================================
-- V025: Externalized configuration tables
-- system_setting, ai_prompt_template, email_template
-- ============================================================

-- ── Table 1: system_setting ─────────────────────────────────

CREATE TABLE system_setting (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category        text NOT NULL,
    setting_key     text NOT NULL UNIQUE,
    setting_value   text NOT NULL,
    value_type      text NOT NULL DEFAULT 'STRING',
    display_name    text NOT NULL,
    description     text NULL,
    editable        boolean NOT NULL DEFAULT true,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid NULL
);
CREATE INDEX idx_system_setting_category ON system_setting(category);

-- ── Table 2: ai_prompt_template ─────────────────────────────

CREATE TABLE ai_prompt_template (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    module          text NOT NULL,
    prompt_name     text NOT NULL,
    prompt_text     text NOT NULL,
    description     text NULL,
    version         int NOT NULL DEFAULT 1,
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid NULL,
    UNIQUE(module, prompt_name, version)
);
CREATE INDEX idx_ai_prompt_template_module ON ai_prompt_template(module, is_active);

-- ── Table 3: email_template ─────────────────────────────────

CREATE TABLE email_template (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key    text NOT NULL UNIQUE,
    subject         text NOT NULL,
    html_body       text NOT NULL,
    description     text NULL,
    available_vars  text NULL,
    is_active       boolean NOT NULL DEFAULT true,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid NULL
);

-- ═══════════════════════════════════════════════════════════
-- SEED DATA: system_setting
-- ═══════════════════════════════════════════════════════════

-- GENERAL
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('GENERAL', 'platform.name', 'AI Marketing Platform', 'STRING', 'Platform Name', 'Name shown in emails and UI'),
('GENERAL', 'platform.url', 'https://adverion.xyz', 'STRING', 'Platform URL', 'Base URL of the platform'),
('GENERAL', 'platform.support.email', 'support@adverion.xyz', 'STRING', 'Support Email', 'Email shown in footers'),
('GENERAL', 'platform.default.timezone', 'Europe/Sofia', 'STRING', 'Default Timezone', 'Default timezone for new clients'),
('GENERAL', 'platform.default.currency', 'EUR', 'STRING', 'Default Currency', 'Default currency for new clients'),
('GENERAL', 'platform.default.language', 'en', 'STRING', 'Default Language', 'Default UI language');

-- AI
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('AI', 'ai.anthropic.default.model', 'claude-sonnet-4-20250514', 'STRING', 'Default AI Model', 'Claude model for standard operations'),
('AI', 'ai.anthropic.complex.model', 'claude-sonnet-4-20250514', 'STRING', 'Complex AI Model', 'Claude model for complex tasks like campaign proposals'),
('AI', 'ai.anthropic.max.tokens', '4096', 'INTEGER', 'Max Output Tokens', 'Maximum tokens in AI response'),
('AI', 'ai.anthropic.timeout.seconds', '60', 'INTEGER', 'AI Timeout (seconds)', 'Timeout for AI API calls'),
('AI', 'ai.cost.limit.daily.usd', '5.00', 'DECIMAL', 'Daily AI Cost Limit ($)', 'Maximum daily AI spend'),
('AI', 'ai.cost.limit.monthly.usd', '100.00', 'DECIMAL', 'Monthly AI Cost Limit ($)', 'Maximum monthly AI spend'),
('AI', 'ai.analyzer.enabled', 'true', 'BOOLEAN', 'Creative Analyzer Enabled', 'Auto-analyze uploaded creatives'),
('AI', 'ai.analyzer.auto.copy', 'true', 'BOOLEAN', 'Auto-Generate Copy', 'Auto-generate copy variants after analysis'),
('AI', 'ai.video.analysis.enabled', 'true', 'BOOLEAN', 'Video Analysis Enabled', 'Enable AI analysis of video creatives'),
('AI', 'ai.video.max.size.mb', '20', 'INTEGER', 'Max Video Size (MB)', 'Maximum video size for AI analysis');

-- OPTIMIZER
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('OPTIMIZER', 'optimizer.enabled', 'true', 'BOOLEAN', 'Optimizer Enabled', 'Enable AI performance optimizer'),
('OPTIMIZER', 'optimizer.min.data.days', '7', 'INTEGER', 'Min Data Days', 'Minimum days of data before budget suggestions'),
('OPTIMIZER', 'optimizer.min.conversions', '30', 'INTEGER', 'Min Conversions', 'Minimum conversions before budget suggestions'),
('OPTIMIZER', 'optimizer.frequency.threshold', '2.0', 'DECIMAL', 'Frequency Threshold', 'Frequency above which fatigue is detected'),
('OPTIMIZER', 'optimizer.ctr.drop.threshold', '0.15', 'DECIMAL', 'CTR Drop Threshold', 'CTR drop fraction that triggers alert (0.15 = 15%)'),
('OPTIMIZER', 'optimizer.cpa.spike.threshold', '0.40', 'DECIMAL', 'CPA Spike Threshold', 'CPA increase fraction that triggers alert (0.40 = 40%)'),
('OPTIMIZER', 'optimizer.budget.change.max.percent', '10', 'INTEGER', 'Max Budget Change %', 'Maximum single budget change percentage'),
('OPTIMIZER', 'optimizer.budget.cumulative.max.percent', '25', 'INTEGER', 'Max Cumulative Budget %', 'Maximum cumulative budget change in 7 days'),
('OPTIMIZER', 'optimizer.cooldown.budget.hours', '72', 'INTEGER', 'Budget Cooldown (hours)', 'Hours between budget suggestions for same entity'),
('OPTIMIZER', 'optimizer.cooldown.pause.hours', '48', 'INTEGER', 'Pause Cooldown (hours)', 'Hours between pause suggestions for same entity'),
('OPTIMIZER', 'optimizer.strong.roas.threshold', '3.0', 'DECIMAL', 'Strong ROAS Threshold', 'ROAS above which scale-up is suggested'),
('OPTIMIZER', 'optimizer.weak.roas.threshold', '1.5', 'DECIMAL', 'Weak ROAS Threshold', 'ROAS below which scale-down is suggested');

-- ANOMALY
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('ANOMALY', 'anomaly.spend.spike.factor', '2.0', 'DECIMAL', 'Spend Spike Factor', 'Daily spend must exceed this multiple of avg to trigger'),
('ANOMALY', 'anomaly.conversion.drop.min.avg', '5.0', 'DECIMAL', 'Conversion Drop Min Avg', 'Minimum avg daily conversions before flagging zero-conversion'),
('ANOMALY', 'anomaly.cpm.surge.factor', '1.5', 'DECIMAL', 'CPM Surge Factor', 'CPM must exceed this multiple of avg to trigger'),
('ANOMALY', 'anomaly.ctr.collapse.factor', '0.50', 'DECIMAL', 'CTR Collapse Factor', 'CTR drop fraction that triggers alert (0.50 = 50%)'),
('ANOMALY', 'anomaly.baseline.days', '14', 'INTEGER', 'Baseline Days', 'Number of baseline days for anomaly comparison');

-- SCHEDULE (NOTE: changing these requires app restart)
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('SCHEDULE', 'schedule.daily.sync.cron', '0 30 3 * * *', 'STRING', 'Daily Sync Time', 'Cron expression for daily Meta sync (UTC). Requires restart.'),
('SCHEDULE', 'schedule.optimizer.cron', '0 0 5 * * *', 'STRING', 'Optimizer Run Time', 'Cron expression for daily optimizer (UTC). Requires restart.'),
('SCHEDULE', 'schedule.weekly.digest.cron', '0 0 9 * * MON', 'STRING', 'Weekly Digest Time', 'Cron expression for weekly client digest (UTC). Requires restart.'),
('SCHEDULE', 'schedule.token.refresh.cron', '0 0 2 * * *', 'STRING', 'Token Refresh Time', 'Cron expression for Meta token refresh (UTC). Requires restart.'),
('SCHEDULE', 'schedule.monthly.report.cron', '0 0 6 1 * *', 'STRING', 'Monthly Report Time', 'Cron expression for monthly report generation (UTC). Requires restart.');

-- META
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('META', 'meta.graph.api.version', 'v19.0', 'STRING', 'Graph API Version', 'Meta Graph API version'),
('META', 'meta.sync.initial.days', '90', 'INTEGER', 'Initial Sync Days', 'Days of data to fetch on initial sync'),
('META', 'meta.sync.daily.days', '7', 'INTEGER', 'Daily Sync Days', 'Days of data to reconcile on daily sync'),
('META', 'meta.sync.manual.days', '30', 'INTEGER', 'Manual Sync Days', 'Days of data to fetch on manual sync'),
('META', 'meta.token.refresh.days.before', '10', 'INTEGER', 'Token Refresh Warning Days', 'Days before expiry to attempt refresh');

-- EMAIL
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('EMAIL', 'email.enabled', 'true', 'BOOLEAN', 'Email Sending Enabled', 'Enable/disable all email sending'),
('EMAIL', 'email.from.address', 'noreply@adverion.xyz', 'STRING', 'From Email Address', 'Sender email address'),
('EMAIL', 'email.from.name', 'AI Marketing Platform', 'STRING', 'From Name', 'Sender display name'),
('EMAIL', 'email.invitation.expiry.hours', '72', 'INTEGER', 'Invitation Expiry (hours)', 'Hours before invitation link expires'),
('EMAIL', 'email.password.reset.expiry.minutes', '60', 'INTEGER', 'Password Reset Expiry (minutes)', 'Minutes before reset link expires');

-- SECURITY
INSERT INTO system_setting (category, setting_key, setting_value, value_type, display_name, description) VALUES
('SECURITY', 'security.access.token.expiry.seconds', '3600', 'INTEGER', 'Access Token Expiry (seconds)', 'JWT access token lifetime'),
('SECURITY', 'security.refresh.token.expiry.days.remember', '30', 'INTEGER', 'Refresh Token (Remember Me) Days', 'Refresh token lifetime when Remember Me is on'),
('SECURITY', 'security.refresh.token.expiry.hours.normal', '24', 'INTEGER', 'Refresh Token (Normal) Hours', 'Refresh token lifetime without Remember Me'),
('SECURITY', 'security.2fa.code.expiry.minutes', '10', 'INTEGER', '2FA Code Expiry (minutes)', 'Two-factor code validity period'),
('SECURITY', 'security.2fa.max.attempts', '5', 'INTEGER', '2FA Max Attempts', 'Max verification attempts before lockout'),
('SECURITY', 'security.2fa.lockout.minutes', '15', 'INTEGER', '2FA Lockout (minutes)', 'Lockout duration after max attempts');

-- ═══════════════════════════════════════════════════════════
-- SEED DATA: ai_prompt_template
-- ═══════════════════════════════════════════════════════════

-- ── CREATIVE_ANALYZER ────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('CREATIVE_ANALYZER', 'system_prompt', $pt$You are an expert digital-advertising creative analyst. Evaluate the provided creative (image or video) for use in paid social / display campaigns. If this is a video, describe: the storyline/narrative, key scenes, text overlays, call to action, pacing/energy level, music mood (if detectable), and overall production quality. Return ONLY a JSON object (no markdown fences, no extra text) with exactly these keys:
{
  "quality_score": <number 0-100>,
  "composition": "<brief assessment of layout, balance, focal point>",
  "color_palette": ["#hex1","#hex2","#hex3"],
  "text_readability": "<assessment of any text in the image/video>",
  "brand_consistency": "<notes on logo placement, brand colours if detectable>",
  "platform_fit": {
    "facebook_feed": "<suitability 1-10 and why>",
    "instagram_story": "<suitability 1-10 and why>",
    "google_display": "<suitability 1-10 and why>"
  },
  "strengths": ["strength1","strength2"],
  "improvements": ["suggestion1","suggestion2"],
  "overall_summary": "<2-3 sentence executive summary>"
}$pt$, 'System prompt for creative image/video analysis');

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('CREATIVE_ANALYZER', 'user_prompt_image', $pt$Analyze this creative asset. File: {filename}, Type: {mime_type}.

Shared client context:
{shared_context}

Provide your analysis as the JSON object described in the system prompt.$pt$, 'User prompt for image creative analysis');

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('CREATIVE_ANALYZER', 'user_prompt_video', $pt$Analyze this video creative. Watch the full video and describe what happens, the visual style, messaging, and how it could be used in advertising. File: {filename}, Type: {mime_type}.

Shared client context:
{shared_context}

Respond ONLY in valid JSON as described in the system prompt.$pt$, 'User prompt for video creative analysis');

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('CREATIVE_ANALYZER', 'user_prompt_video_fallback', $pt$This is a video creative that is too large to analyze visually. Based on metadata: filename={filename}, size={file_size} bytes, mime={mime_type}. Provide analysis based on what you can infer.

Shared client context:
{shared_context}

Respond ONLY in valid JSON as described in the system prompt.$pt$, 'User prompt for oversized video fallback analysis');

-- ── COPY_FACTORY ──────────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('COPY_FACTORY', 'system_prompt', $pt$You are an expert digital-advertising copywriter. Generate ad-copy variants based on the provided context. Return ONLY a JSON array (no markdown fences, no extra text) of exactly 5 objects, each with these keys:
[
  {
    "platform": "<target platform>",
    "language": "en",
    "primary_text": "<main ad body text, max 125 chars>",
    "headline": "<headline, max 40 chars>",
    "description": "<description line, max 30 chars>",
    "cta": "<call to action, e.g. LEARN_MORE, SHOP_NOW, SIGN_UP, GET_OFFER, CONTACT_US>"
  }
]
Generate variants for these platforms in order: Facebook Feed, Instagram Story, Google Display, LinkedIn Sponsored, General/Multi-platform.$pt$, 'System prompt for ad copy generation');

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('COPY_FACTORY', 'user_prompt', $pt$Generate 5 ad-copy variants for this creative.

--- Client Context ---
{shared_context}

--- Best Historical Ad Texts ---
{best_ads}

--- Creative Analysis ---
{creative_analysis}

Create compelling, platform-optimized copy that aligns with the brand and leverages the creative's strengths.$pt$, 'User prompt template for ad copy generation');

-- ── OPTIMIZER_ENRICHMENT ──────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('OPTIMIZER_ENRICHMENT', 'system_prompt', $pt$You are a performance marketing expert. Explain this optimization finding to an agency account manager in a clear, actionable way. Be specific about what they should do and why. Write 2-4 sentences. Be direct and professional. Language: Use the same language as the client name suggests (Bulgarian if Cyrillic, English otherwise).$pt$, 'System prompt for optimizer finding LLM enrichment');

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('OPTIMIZER_ENRICHMENT', 'user_prompt', $pt$Shared context:
{shared_context}

Finding type: {finding_type}
Risk: {risk_level}
Scope: {scope}
Details: {details}
Data: {data}$pt$, 'User prompt template for optimizer finding enrichment');

-- ── CAMPAIGN_CREATOR ──────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('CAMPAIGN_CREATOR', 'system_prompt', $pt$You are a senior Meta Ads media buyer with 10+ years of experience.
Create a detailed campaign proposal in STRICT JSON format.

The JSON must have this exact structure:
{
  "campaign_name": "string",
  "objective": "OUTCOME_SALES|OUTCOME_LEADS|OUTCOME_TRAFFIC|OUTCOME_AWARENESS|OUTCOME_ENGAGEMENT|OUTCOME_APP_PROMOTION",
  "budget_type": "ABO|CBO",
  "campaign_daily_budget": number or null,
  "rationale": "string explaining your strategic reasoning",
  "suggested_daily_budget": number,
  "estimated_results": "string with expected KPIs",
  "warnings": ["array of warnings or caveats"],
  "adsets": [
    {
      "name": "string",
      "daily_budget": number,
      "targeting": {"age_min": 25, "age_max": 55, "genders": [1,2], "interests": ["..."]},
      "optimization_goal": "CONVERSIONS|LINK_CLICKS|IMPRESSIONS|REACH",
      "ads": [
        {
          "name": "string",
          "creative_asset_id": "UUID or null",
          "primary_text": "string (max 125 chars for best performance)",
          "headline": "string (max 40 chars)",
          "description": "string (max 30 chars)",
          "cta": "LEARN_MORE|SHOP_NOW|SIGN_UP|GET_OFFER|CONTACT_US",
          "url": "https://..."
        }
      ]
    }
  ]
}

Rules:
- Use ONLY these exact Meta objective values: OUTCOME_SALES, OUTCOME_LEADS, OUTCOME_TRAFFIC, OUTCOME_AWARENESS, OUTCOME_ENGAGEMENT, OUTCOME_APP_PROMOTION
- Use OUTCOME_SALES for purchase or conversion campaigns
- Use OUTCOME_LEADS for lead generation campaigns
- Use OUTCOME_TRAFFIC for website traffic campaigns
- Use OUTCOME_AWARENESS for brand awareness or reach campaigns
- Use OUTCOME_ENGAGEMENT for post engagement or video view campaigns
- Use OUTCOME_APP_PROMOTION for app install or app promotion campaigns
- Reference existing creative_asset_id UUIDs from the provided list when available
- Reuse approved copy variants from the provided context when possible instead of inventing new ad text
- If no creatives are available, set creative_asset_id to null and add a warning
- Respect the requested budget type from the context
- If budget_type is CBO, use campaign_daily_budget for the main budget and still create multiple adsets
- Create 2-4 adsets with different targeting segments
- Each adset should have 2-3 ads (for A/B testing)
- Budget allocation should be strategic across adsets
- Consider historical performance when setting expectations
- Campaign name must be unique and descriptive
- Respond ONLY with the JSON object, no markdown or extra text$pt$, 'System prompt for AI campaign proposal generation');

-- ── AI_REPORTER ───────────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('AI_REPORTER', 'system_prompt', $pt$You are a marketing consultant writing a monthly performance report.
Based on the provided data, write the following sections:
1) Executive Summary (3-5 sentences overview of overall performance)
2) Campaign Highlights (notable campaigns, launches, wins)
3) Areas for Improvement (metrics that declined or need attention)
4) Actions Taken (AI optimizations applied, campaign changes made)
5) Recommendations for Next Month (actionable next steps)

Be data-driven and reference specific numbers from the data.
Keep each section concise but insightful. Use the client's language.

Respond with STRICT JSON only, no markdown:
{
  "executiveSummary": "...",
  "campaignHighlights": "...",
  "areasForImprovement": "...",
  "actionsTaken": "...",
  "recommendations": "..."
}$pt$, 'System prompt for monthly AI report generation');

-- ── CLIENT_BRIEFER ────────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('CLIENT_BRIEFER', 'system_prompt', $pt$You are a marketing strategist analysing a business website.
Based on the provided website text (or URL if text is unavailable), return a JSON object with:
{
  "industry": "string — specific industry/niche",
  "business_model": "ECOM | SERVICE | B2B | LOCAL | LUXURY | SAAS | OTHER",
  "usp": "string — unique selling proposition (1-2 sentences)",
  "target_audiences": ["audience 1", "audience 2", ...],
  "tone_of_voice": "string — brand tone description",
  "offers": ["product/service 1", "product/service 2", ...],
  "competitors": ["competitor 1", ...],
  "suggested_strategy": "string — 2-3 sentence Meta Ads strategy recommendation",
  "suggested_monthly_budget_range": "string — e.g. '$2,000 - $5,000'",
  "brand_colors": "string — if detectable from the site, else null",
  "languages": ["string — detected content languages"]
}

Respond with STRICT JSON only, no markdown fences, no extra text.
If you cannot determine a field, use null or an empty array.$pt$, 'System prompt for client website briefing analysis');

-- ── AUDIENCE_ARCHITECT ────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('AUDIENCE_ARCHITECT', 'system_prompt', $pt$You are a Meta Ads targeting expert and audience strategist.
Based on the shared client context and targeting data, suggest 3-5 audience segments.
For each audience, provide detailed Meta Ads targeting specifications.

Respond with STRICT JSON only, no markdown:
{
  "recommended_audiences": [
    {
      "name": "string — descriptive audience name",
      "description": "string — who this audience is",
      "targeting": {
        "age_min": 18,
        "age_max": 65,
        "genders": [0],
        "geo_locations": { "countries": ["US"] },
        "interests": [{"id": "6003...", "name": "Interest name"}],
        "custom_audiences": [],
        "excluded_custom_audiences": []
      },
      "estimated_size": "string — e.g. '500K - 1M'",
      "rationale": "string — why this audience fits the business",
      "confidence": "HIGH | MEDIUM | LOW",
      "suggested_daily_budget": "string — e.g. '$20-$50'",
      "funnel_stage": "TOFU | MOFU | BOFU"
    }
  ],
  "exclusion_recommendations": [
    {
      "description": "string — what to exclude and why",
      "targeting_spec": "string — the exclusion targeting"
    }
  ],
  "overlap_warnings": [
    "string — warning about audience overlap between segments"
  ],
  "strategy_notes": "string — overall targeting strategy advice"
}$pt$, 'System prompt for audience targeting recommendations');

-- ── BUDGET_STRATEGIST ─────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('BUDGET_STRATEGIST', 'system_prompt', $pt$You are a senior media buyer reviewing a client's budget performance data.
Based on the analysis provided, write a concise 3-5 paragraph strategic recommendation.
Cover: pacing, day-of-week optimization, campaign rebalancing, and diminishing returns.
Be specific with numbers. Use a professional but actionable tone.
Do NOT use markdown. Plain text only.$pt$, 'System prompt for budget strategy recommendations');

-- ── CLIENT_PORTAL_AI ──────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('CLIENT_PORTAL_AI', 'system_prompt', $pt$You are a friendly marketing analytics assistant for a digital advertising client.
You ONLY answer using the data provided below — never fabricate numbers.
If the data does not contain enough information to answer, say so honestly.
Keep answers concise (3-6 sentences) with specific numbers where possible.
Use a professional but approachable tone.
Format currency as USD, percentages with one decimal place.

=== CLIENT DATA ===
{client_data}$pt$, 'System prompt for client portal AI chat assistant');

-- ── WEEKLY_DIGEST ─────────────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('WEEKLY_DIGEST', 'system_prompt', $pt$You are a friendly marketing performance assistant writing a weekly summary email.
Write a brief weekly performance summary (5-8 sentences).
Include: key metrics with actual numbers, highlight of the week, what was optimized.
Use a friendly professional tone — like a trusted advisor sharing results.

Respond with STRICT JSON only, no markdown:
{
  "subjectLine": "Your Weekly Performance Summary — [key highlight]",
  "greeting": "Hi [client name] team,",
  "body": "... the 5-8 sentence summary ...",
  "signoff": "Best regards,\nYour AI Marketing Platform"
}$pt$, 'System prompt for weekly client digest email generation');

-- ── AGENCY_INTELLIGENCE ───────────────────────────────────────

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('AGENCY_INTELLIGENCE', 'system_prompt', $pt$You are a strategic advisor to a platform owner who manages multiple advertising agencies.
Analyze the intelligence data below and provide a concise executive briefing (4-8 sentences).
Highlight: top-performing industries, agencies needing attention, urgent churn risks.
Be specific with numbers. Use a professional, actionable tone.

=== INTELLIGENCE DATA ===
{intelligence_data}$pt$, 'System prompt for owner-level agency intelligence briefing');

INSERT INTO ai_prompt_template (module, prompt_name, prompt_text, description) VALUES
('AGENCY_INTELLIGENCE', 'user_prompt', 'Provide an executive intelligence briefing.', 'User prompt for agency intelligence briefing');

-- ═══════════════════════════════════════════════════════════
-- SEED DATA: email_template
-- ═══════════════════════════════════════════════════════════

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('INVITATION', $et$You're Invited to AI Marketing Platform!$et$,
$et$<h2 style="color:#333333; margin-top:0;">You've Been Invited! 🎉</h2>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    You've been invited to <strong>AI Marketing Platform</strong> by <strong>{{agencyName}}</strong>.
</p>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    Your role: <span style="background-color:#E3F2FD; padding:2px 8px; border-radius:4px; font-weight:bold;">{{role}}</span>
</p>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    Click the button below to activate your account and set your password:
</p>
<div style="text-align:center; margin:30px 0;">
    <a href="{{activationLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
        Activate My Account
    </a>
</div>
<p style="color:#999999; font-size:13px;">
    This invitation expires in 72 hours. If you didn't expect this invitation, you can safely ignore this email.
</p>
<p style="color:#999999; font-size:12px;">
    If the button doesn't work, copy and paste this link:<br>
    <a href="{{activationLink}}" style="color:#1565C0;">{{activationLink}}</a>
</p>$et$, 'Invitation email sent to new users', 'agencyName,role,activationLink');

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('PASSWORD_RESET', $et$Reset Your Password — AI Marketing Platform$et$,
$et$<h2 style="color:#333333; margin-top:0;">Password Reset 🔑</h2>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    We received a request to reset your password for AI Marketing Platform.
</p>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    Click the button below to set a new password:
</p>
<div style="text-align:center; margin:30px 0;">
    <a href="{{resetLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
        Reset Password
    </a>
</div>
<p style="color:#999999; font-size:13px;">
    This link expires in 1 hour. If you didn't request a password reset, you can safely ignore this email.
</p>
<p style="color:#999999; font-size:12px;">
    If the button doesn't work, copy and paste this link:<br>
    <a href="{{resetLink}}" style="color:#1565C0;">{{resetLink}}</a>
</p>$et$, 'Password reset email', 'resetLink');

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('WELCOME', $et$Welcome to AI Marketing Platform!$et$,
$et$<h2 style="color:#333333; margin-top:0;">Welcome to AI Marketing Platform! 🎉</h2>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    Hi <strong>{{displayName}}</strong>,
</p>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    {{welcomeMessage}}
</p>
<div style="text-align:center; margin:30px 0;">
    <a href="{{loginLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
        Go to Dashboard
    </a>
</div>
<p style="color:#999999; font-size:13px;">
    If you have any questions, reach out to your account manager.
</p>$et$, 'Welcome email for new activated accounts', 'displayName,welcomeMessage,loginLink');

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('REPORT_SENT', $et$📊 Performance Report — {{clientName}}$et$,
$et$<h2 style="color:#333333; margin-top:0;">📊 Performance Report</h2>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    A new performance report is available for <strong>{{clientName}}</strong>.
</p>
<p style="color:#555555; font-size:14px; line-height:1.6;">
    Period: <strong>{{period}}</strong>
</p>
<table role="presentation" width="100%" cellpadding="8" cellspacing="0" style="border-collapse:collapse; margin:20px 0;">
    <tr style="background-color:#E3F2FD;">
        <td style="border:1px solid #BBDEFB; font-weight:bold; color:#1565C0;">Total Spend</td>
        <td style="border:1px solid #BBDEFB;">{{spend}}</td>
    </tr>
    <tr>
        <td style="border:1px solid #E0E0E0; font-weight:bold; color:#1565C0;">Conversions</td>
        <td style="border:1px solid #E0E0E0;">{{conversions}}</td>
    </tr>
    <tr style="background-color:#E3F2FD;">
        <td style="border:1px solid #BBDEFB; font-weight:bold; color:#1565C0;">ROAS</td>
        <td style="border:1px solid #BBDEFB;">{{roas}}</td>
    </tr>
</table>
<div style="text-align:center; margin:30px 0;">
    <a href="{{portalLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
        View Full Report
    </a>
</div>$et$, 'Performance report notification email', 'clientName,period,spend,conversions,roas,portalLink');

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('ALERT', $et$⚠️ {{alertTitle}} — {{clientName}}$et$,
$et$<h2 style="color:#D32F2F; margin-top:0;">⚠️ {{alertTitle}}</h2>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    {{alertMessage}}
</p>
<p style="color:#555555; font-size:14px; line-height:1.4;">
    <strong>Client:</strong> {{clientName}}<br>
    <strong>Severity:</strong> <span style="color:{{severityColor}}; font-weight:bold;">{{severity}}</span>
</p>
<div style="text-align:center; margin:30px 0;">
    <a href="{{dashboardLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
        View Details
    </a>
</div>
<p style="color:#999999; font-size:12px;">
    You are receiving this alert because you are assigned to this client.
</p>$et$, 'Alert notification email for anomalies and issues', 'alertTitle,alertMessage,clientName,severity,severityColor,dashboardLink');

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('CAMPAIGN_PUBLISHED', $et$🚀 Campaign Published — {{campaignName}}$et$,
$et$<h2 style="color:#333333; margin-top:0;">🚀 Campaign Published</h2>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    Campaign <strong>{{campaignName}}</strong> has been successfully published to Meta Ads for <strong>{{clientName}}</strong>.
</p>
<p style="color:#555555; font-size:14px; line-height:1.4;">
    <strong>Status:</strong> <span style="background-color:#C8E6C9; padding:2px 8px; border-radius:4px; color:#2E7D32; font-weight:bold;">PUBLISHED</span>
</p>
<div style="text-align:center; margin:30px 0;">
    <a href="{{dashboardLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
        View Campaign
    </a>
</div>$et$, 'Campaign published confirmation email', 'campaignName,clientName,dashboardLink');

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('TWO_FACTOR_CODE', 'Your Verification Code',
$et$<h2 style="color:#333333; margin-top:0;">🔐 Verification Code</h2>
<p style="color:#555555; font-size:16px; line-height:1.6;">
    Your verification code is:
</p>
<div style="text-align:center; margin:30px 0;">
    <div style="display:inline-block; background-color:#E3F2FD; border:2px solid #1565C0; border-radius:8px; padding:16px 32px; letter-spacing:12px; font-size:32px; font-weight:bold; color:#0D47A1; font-family:'Courier New',monospace;">
        {{code}}
    </div>
</div>
<p style="color:#555555; font-size:16px; line-height:1.6; text-align:center;">
    This code is valid for <strong>10 minutes</strong>.
</p>
<p style="color:#999999; font-size:13px; margin-top:30px;">
    If you didn't request this code, please ignore this email
    and consider changing your password.
</p>$et$, 'Two-factor authentication verification code', 'code');

INSERT INTO email_template (template_key, subject, html_body, description, available_vars) VALUES
('OUTER_WRAPPER', '', $et$<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0; padding:0; background-color:#f5f5f5; font-family:Arial,Helvetica,sans-serif;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f5f5f5;">
        <tr>
            <td align="center" style="padding:40px 0;">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1);">
                    <tr>
                        <td style="background:linear-gradient(135deg,#1565C0,#0D47A1); padding:30px; text-align:center;">
                            <h1 style="color:#ffffff; margin:0; font-size:24px; font-weight:bold;">
                                🚀 {{platform_name}}
                            </h1>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding:30px;">
                            {{content}}
                        </td>
                    </tr>
                    <tr>
                        <td style="background-color:#f9f9f9; padding:20px 30px; text-align:center; border-top:1px solid #eeeeee;">
                            <p style="color:#999999; font-size:12px; margin:0;">
                                © 2026 Adverion. All rights reserved.<br>
                                This email was sent by {{platform_name}}.
                            </p>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>$et$, 'Outer HTML wrapper for all emails', 'platform_name,content');
