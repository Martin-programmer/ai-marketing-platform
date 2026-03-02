-- ============================================================
-- V002__meta.sql
-- Meta integration tables: meta_connection, meta_sync_job
-- ============================================================

-- 1. meta_connection
CREATE TABLE meta_connection (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    ad_account_id       text            NOT NULL,
    pixel_id            text            NULL,
    page_id             text            NULL,
    access_token_enc    bytea           NOT NULL,
    token_key_id        text            NOT NULL,
    status              text            NOT NULL,       -- CONNECTED | DISCONNECTED | ERROR
    connected_at        timestamptz     NOT NULL,
    last_sync_at        timestamptz     NULL,
    last_error_code     text            NULL,
    last_error_message  text            NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_meta_connection PRIMARY KEY (id),
    CONSTRAINT fk_meta_connection_client FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE RESTRICT,
    CONSTRAINT uq_meta_connection_client UNIQUE (client_id)
);

CREATE INDEX idx_meta_connection_agency_status ON meta_connection (agency_id, status);
CREATE INDEX idx_meta_connection_client ON meta_connection (client_id);

-- 2. meta_sync_job
CREATE TABLE meta_sync_job (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    job_type            text            NOT NULL,       -- INITIAL | DAILY | MANUAL
    job_status          text            NOT NULL,       -- PENDING | RUNNING | SUCCESS | FAILED
    idempotency_key     text            NOT NULL,
    requested_at        timestamptz     NOT NULL,
    started_at          timestamptz     NULL,
    finished_at         timestamptz     NULL,
    stats_json          jsonb           NULL,
    error_json          jsonb           NULL,

    CONSTRAINT pk_meta_sync_job PRIMARY KEY (id),
    CONSTRAINT uq_meta_sync_job_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_meta_sync_job_agency_client_status ON meta_sync_job (agency_id, client_id, job_status);
