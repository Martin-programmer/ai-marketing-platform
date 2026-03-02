-- ============================================================
-- V005__ai_reports.sql
-- AI & reporting tables: ai_suggestion, ai_action_log,
--                        report, feedback
-- ============================================================

-- 1. ai_suggestion
CREATE TABLE ai_suggestion (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    scope_type          text            NOT NULL,       -- CLIENT | CAMPAIGN | ADSET | AD
    scope_id            uuid            NOT NULL,
    suggestion_type     text            NOT NULL,       -- BUDGET_ADJUST | PAUSE | ENABLE | CREATIVE_TEST | DIAGNOSTIC
    payload_json        jsonb           NOT NULL,
    rationale           text            NOT NULL,
    confidence          numeric(4,3)    NOT NULL,
    risk_level          text            NOT NULL,       -- LOW | MEDIUM | HIGH
    status              text            NOT NULL,       -- PENDING | APPROVED | REJECTED | APPLIED | FAILED
    cooldown_until      timestamptz     NULL,
    created_by          text            NOT NULL,       -- AI | USER
    created_at          timestamptz     NOT NULL DEFAULT now(),
    reviewed_by         uuid            NULL,
    reviewed_at         timestamptz     NULL,

    CONSTRAINT pk_ai_suggestion PRIMARY KEY (id)
);

CREATE INDEX idx_ai_suggestion_agency_client_status ON ai_suggestion (agency_id, client_id, status);
CREATE INDEX idx_ai_suggestion_client_created ON ai_suggestion (client_id, created_at);

-- 2. ai_action_log
CREATE TABLE ai_action_log (
    id                      uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id               uuid            NOT NULL,
    client_id               uuid            NOT NULL,
    suggestion_id           uuid            NOT NULL,
    executed_by             text            NOT NULL,       -- AI | USER
    meta_request_json       jsonb           NOT NULL,
    meta_response_json      jsonb           NULL,
    success                 boolean         NOT NULL,
    result_snapshot_json    jsonb           NULL,
    created_at              timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_ai_action_log PRIMARY KEY (id),
    CONSTRAINT fk_ai_action_log_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES ai_suggestion (id) ON DELETE RESTRICT
);

CREATE INDEX idx_ai_action_log_suggestion ON ai_action_log (suggestion_id);
CREATE INDEX idx_ai_action_log_client_created ON ai_action_log (client_id, created_at);

-- 3. report
CREATE TABLE report (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    report_type         text            NOT NULL,       -- DAILY | WEEKLY | MONTHLY
    period_start        date            NOT NULL,
    period_end          date            NOT NULL,
    status              text            NOT NULL,       -- DRAFT | IN_REVIEW | APPROVED | SENT
    html_content        text            NOT NULL,
    pdf_s3_key          text            NULL,
    created_by          uuid            NOT NULL,
    approved_by         uuid            NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    approved_at         timestamptz     NULL,
    sent_at             timestamptz     NULL,

    CONSTRAINT pk_report PRIMARY KEY (id),
    CONSTRAINT uq_report_client_type_period UNIQUE (client_id, report_type, period_start, period_end)
);

CREATE INDEX idx_report_agency_client_period ON report (agency_id, client_id, period_start, period_end);

-- 4. feedback
CREATE TABLE feedback (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    source_type         text            NOT NULL,       -- SUGGESTION | REPORT
    source_id           uuid            NOT NULL,
    rating              integer         NOT NULL,
    comment             text            NULL,
    created_by          uuid            NOT NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_feedback PRIMARY KEY (id)
);

CREATE INDEX idx_feedback_client_source ON feedback (client_id, source_type);
