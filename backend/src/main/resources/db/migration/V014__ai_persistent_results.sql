-- Store audience suggestions so other AI modules can reference them
CREATE TABLE ai_audience_suggestion (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id       uuid        NOT NULL,
    client_id       uuid        NOT NULL,
    suggestion_json jsonb       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_aud_sugg_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_audience_client ON ai_audience_suggestion(client_id, created_at DESC);

-- Store budget analysis results
CREATE TABLE ai_budget_analysis (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id       uuid        NOT NULL,
    client_id       uuid        NOT NULL,
    analysis_json   jsonb       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_budget_analysis_client FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_budget_client ON ai_budget_analysis(client_id, created_at DESC);
