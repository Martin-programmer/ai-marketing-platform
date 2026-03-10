CREATE TABLE ai_prompt_log (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id           uuid            NULL,
    client_id           uuid            NULL,
    module              text            NOT NULL,
    model               text            NOT NULL,
    prompt_tokens       int             NOT NULL DEFAULT 0,
    completion_tokens   int             NOT NULL DEFAULT 0,
    total_tokens        int             NOT NULL DEFAULT 0,
    cost_usd            numeric(10,6)   NOT NULL DEFAULT 0,
    duration_ms         int             NOT NULL DEFAULT 0,
    success             boolean         NOT NULL,
    error_message       text            NULL,
    created_at          timestamptz     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_prompt_log_module ON ai_prompt_log(module, created_at DESC);
CREATE INDEX idx_ai_prompt_log_agency ON ai_prompt_log(agency_id, created_at DESC);
