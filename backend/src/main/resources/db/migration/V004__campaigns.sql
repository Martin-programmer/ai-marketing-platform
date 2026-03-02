-- ============================================================
-- V004__campaigns.sql
-- Campaign tables: campaign, adset, ad, insight_daily
-- ============================================================

-- 1. campaign
CREATE TABLE campaign (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    platform            text            NOT NULL,       -- META
    meta_campaign_id    text            NULL,
    name                text            NOT NULL,
    objective           text            NOT NULL,
    status              text            NOT NULL,       -- DRAFT | PUBLISHED | PAUSED | ARCHIVED
    created_by          uuid            NOT NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_campaign PRIMARY KEY (id),
    CONSTRAINT uq_campaign_client_meta UNIQUE (client_id, meta_campaign_id)
);

CREATE INDEX idx_campaign_agency_client_status ON campaign (agency_id, client_id, status);

-- 2. adset
CREATE TABLE adset (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    campaign_id         uuid            NOT NULL,
    meta_adset_id       text            NULL,
    name                text            NOT NULL,
    daily_budget        numeric(18,2)   NOT NULL,
    targeting_json      jsonb           NOT NULL,
    status              text            NOT NULL,       -- DRAFT | PUBLISHED | PAUSED | ARCHIVED
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_adset PRIMARY KEY (id),
    CONSTRAINT fk_adset_campaign FOREIGN KEY (campaign_id)
        REFERENCES campaign (id) ON DELETE RESTRICT,
    CONSTRAINT uq_adset_client_meta UNIQUE (client_id, meta_adset_id)
);

CREATE INDEX idx_adset_campaign ON adset (campaign_id);

-- 3. ad
CREATE TABLE ad (
    id                          uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id                   uuid            NOT NULL,
    client_id                   uuid            NOT NULL,
    adset_id                    uuid            NOT NULL,
    meta_ad_id                  text            NULL,
    name                        text            NOT NULL,
    creative_package_item_id    uuid            NULL,
    status                      text            NOT NULL,       -- DRAFT | PUBLISHED | PAUSED | ARCHIVED
    created_at                  timestamptz     NOT NULL DEFAULT now(),
    updated_at                  timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_ad PRIMARY KEY (id),
    CONSTRAINT fk_ad_adset FOREIGN KEY (adset_id)
        REFERENCES adset (id) ON DELETE RESTRICT,
    CONSTRAINT uq_ad_client_meta UNIQUE (client_id, meta_ad_id)
);

CREATE INDEX idx_ad_adset ON ad (adset_id);

-- 4. insight_daily
CREATE TABLE insight_daily (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    entity_type         text            NOT NULL,       -- CAMPAIGN | ADSET | AD
    entity_id           uuid            NOT NULL,
    date                date            NOT NULL,
    spend               numeric(18,2)   NOT NULL,
    impressions         bigint          NOT NULL,
    clicks              bigint          NOT NULL,
    ctr                 numeric(10,6)   NOT NULL,
    cpc                 numeric(18,6)   NOT NULL,
    cpm                 numeric(18,6)   NOT NULL,
    conversions         numeric(18,6)   NOT NULL,
    conversion_value    numeric(18,2)   NOT NULL,
    roas                numeric(18,6)   NOT NULL,
    frequency           numeric(18,6)   NOT NULL,
    reach               bigint          NOT NULL,
    raw_json            jsonb           NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_insight_daily PRIMARY KEY (id),
    CONSTRAINT uq_insight_daily_entity_date UNIQUE (entity_type, entity_id, date)
);

CREATE INDEX idx_insight_daily_agency_client_date ON insight_daily (agency_id, client_id, date);
