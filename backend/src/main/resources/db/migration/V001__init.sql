-- ============================================================
-- V001__init.sql
-- Core tables: agency, user_account, client, client_profile,
--              user_client_permission, audit_log
-- ============================================================

-- 1. agency
CREATE TABLE agency (
    id              uuid            NOT NULL DEFAULT gen_random_uuid(),
    name            text            NOT NULL,
    status          text            NOT NULL,       -- ACTIVE | SUSPENDED | DELETED
    plan_code       text            NOT NULL,       -- FREE | PRO | ENTERPRISE
    created_at      timestamptz     NOT NULL DEFAULT now(),
    updated_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_agency PRIMARY KEY (id),
    CONSTRAINT uq_agency_name UNIQUE (name)
);

CREATE INDEX idx_agency_status ON agency (status);

-- 2. user_account
CREATE TABLE user_account (
    id              uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id       uuid            NULL,
    client_id       uuid            NULL,
    role            text            NOT NULL,       -- OWNER_ADMIN | AGENCY_ADMIN | AGENCY_USER | CLIENT_USER
    email           text            NOT NULL,
    cognito_sub     text            NOT NULL,
    status          text            NOT NULL,       -- ACTIVE | INVITED | DISABLED
    created_at      timestamptz     NOT NULL DEFAULT now(),
    updated_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_account PRIMARY KEY (id),
    CONSTRAINT uq_user_account_email UNIQUE (email),
    CONSTRAINT uq_user_account_cognito_sub UNIQUE (cognito_sub),
    CONSTRAINT fk_user_account_agency FOREIGN KEY (agency_id)
        REFERENCES agency (id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_account_client_user
        CHECK (role <> 'CLIENT_USER' OR (client_id IS NOT NULL AND agency_id IS NOT NULL)),
    CONSTRAINT chk_user_account_owner_admin
        CHECK (role <> 'OWNER_ADMIN' OR agency_id IS NULL)
);

CREATE INDEX idx_user_account_agency_role ON user_account (agency_id, role);
CREATE INDEX idx_user_account_client ON user_account (client_id);

-- 3. client
CREATE TABLE client (
    id              uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id       uuid            NOT NULL,
    name            text            NOT NULL,
    industry        text            NULL,
    status          text            NOT NULL,       -- ACTIVE | PAUSED | INACTIVE
    timezone        text            NOT NULL DEFAULT 'Europe/Sofia',
    currency        text            NOT NULL DEFAULT 'BGN',
    created_at      timestamptz     NOT NULL DEFAULT now(),
    updated_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_client PRIMARY KEY (id),
    CONSTRAINT fk_client_agency FOREIGN KEY (agency_id)
        REFERENCES agency (id) ON DELETE RESTRICT,
    CONSTRAINT uq_client_agency_name UNIQUE (agency_id, name)
);

CREATE INDEX idx_client_agency_status ON client (agency_id, status);

-- back-reference FK from user_account.client_id -> client.id
-- (added after client table exists)
ALTER TABLE user_account
    ADD CONSTRAINT fk_user_account_client FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE RESTRICT;

-- 4. client_profile
CREATE TABLE client_profile (
    id              uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id       uuid            NOT NULL,
    client_id       uuid            NOT NULL,
    website         text            NULL,
    profile_json    jsonb           NOT NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    updated_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_client_profile PRIMARY KEY (id),
    CONSTRAINT fk_client_profile_client FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE RESTRICT,
    CONSTRAINT uq_client_profile_client UNIQUE (client_id)
);

CREATE INDEX idx_client_profile_agency_client ON client_profile (agency_id, client_id);

-- 5. user_client_permission
CREATE TABLE user_client_permission (
    user_id         uuid            NOT NULL,
    client_id       uuid            NOT NULL,
    permission      text            NOT NULL,       -- READ | WRITE | APPROVE | ADMIN
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_client_permission PRIMARY KEY (user_id, client_id, permission),
    CONSTRAINT fk_ucp_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ucp_client FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE RESTRICT
);

CREATE INDEX idx_ucp_client_permission ON user_client_permission (client_id, permission);

-- 6. audit_log
CREATE TABLE audit_log (
    id              uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id       uuid            NULL,
    client_id       uuid            NULL,
    actor_user_id   uuid            NULL,
    actor_role      text            NOT NULL,
    action          text            NOT NULL,
    entity_type     text            NOT NULL,
    entity_id       uuid            NULL,
    before_json     jsonb           NULL,
    after_json      jsonb           NULL,
    correlation_id  text            NOT NULL,
    ip              text            NULL,
    user_agent      text            NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_log_agency_created ON audit_log (agency_id, created_at);
CREATE INDEX idx_audit_log_client_created ON audit_log (client_id, created_at);
CREATE INDEX idx_audit_log_correlation ON audit_log (correlation_id);
