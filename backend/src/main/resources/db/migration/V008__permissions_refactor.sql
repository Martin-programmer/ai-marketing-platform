-- ============================================================
-- V008__permissions_refactor.sql
-- Refactor: granular permissions for AGENCY_USER per client
-- ============================================================

-- 1. Drop old user_client_permission table (composite PK: user_id, client_id, permission)
--    Old permissions (READ, WRITE, APPROVE, ADMIN) are being replaced with granular ones.
DROP TABLE IF EXISTS user_client_permission;

-- 2. Recreate with new structure (UUID PK, granted_by, unique constraint)
CREATE TABLE user_client_permission (
    id              uuid            NOT NULL DEFAULT gen_random_uuid(),
    user_id         uuid            NOT NULL,
    client_id       uuid            NOT NULL,
    permission      text            NOT NULL,
    granted_by      uuid            NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_client_permission PRIMARY KEY (id),
    CONSTRAINT fk_ucp_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ucp_client FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE CASCADE,
    CONSTRAINT fk_ucp_granted_by FOREIGN KEY (granted_by)
        REFERENCES user_account (id) ON DELETE SET NULL,
    CONSTRAINT uq_ucp_user_client_perm UNIQUE (user_id, client_id, permission)
);

CREATE INDEX idx_ucp_user ON user_client_permission (user_id);
CREATE INDEX idx_ucp_client ON user_client_permission (client_id);
CREATE INDEX idx_ucp_user_client ON user_client_permission (user_id, client_id);

-- Valid permissions:
--   CLIENT_VIEW, CLIENT_EDIT,
--   CAMPAIGNS_VIEW, CAMPAIGNS_EDIT, CAMPAIGNS_PUBLISH,
--   CREATIVES_VIEW, CREATIVES_EDIT,
--   REPORTS_VIEW, REPORTS_EDIT, REPORTS_SEND,
--   META_MANAGE,
--   AI_VIEW, AI_APPROVE
