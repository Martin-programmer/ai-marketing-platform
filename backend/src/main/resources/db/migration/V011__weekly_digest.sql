-- Weekly digest table for AI-generated email summaries
CREATE TABLE ai_weekly_digest (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id       UUID NOT NULL REFERENCES agency(id),
    client_id       UUID NOT NULL REFERENCES client(id),
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    subject_line    VARCHAR(500),
    html_content    TEXT NOT NULL,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_weekly_digest_agency_client ON ai_weekly_digest(agency_id, client_id);
CREATE INDEX idx_weekly_digest_created       ON ai_weekly_digest(created_at DESC);
