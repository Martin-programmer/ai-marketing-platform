-- ============================================================
-- V003__creatives.sql
-- Creative tables: creative_asset, creative_analysis,
--                  copy_variant, creative_package,
--                  creative_package_item
-- ============================================================

-- 1. creative_asset
CREATE TABLE creative_asset (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    asset_type          text            NOT NULL,       -- IMAGE | VIDEO | DOC
    s3_bucket           text            NOT NULL,
    s3_key              text            NOT NULL,
    original_filename   text            NOT NULL,
    mime_type           text            NOT NULL,
    size_bytes          bigint          NOT NULL,
    width_px            integer         NULL,
    height_px           integer         NULL,
    duration_ms         integer         NULL,
    checksum_sha256     text            NOT NULL,
    status              text            NOT NULL,       -- UPLOADING | READY | ANALYZING | FAILED
    created_by          uuid            NOT NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_creative_asset PRIMARY KEY (id),
    CONSTRAINT fk_creative_asset_client FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE RESTRICT,
    CONSTRAINT fk_creative_asset_created_by FOREIGN KEY (created_by)
        REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT uq_creative_asset_s3 UNIQUE (s3_bucket, s3_key)
);

CREATE INDEX idx_creative_asset_agency_client_created ON creative_asset (agency_id, client_id, created_at);

-- 2. creative_analysis
CREATE TABLE creative_analysis (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    creative_asset_id   uuid            NOT NULL,
    analysis_json       jsonb           NOT NULL,
    quality_score       numeric(5,2)    NOT NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_creative_analysis PRIMARY KEY (id),
    CONSTRAINT fk_creative_analysis_asset FOREIGN KEY (creative_asset_id)
        REFERENCES creative_asset (id) ON DELETE RESTRICT,
    CONSTRAINT uq_creative_analysis_asset UNIQUE (creative_asset_id)
);

CREATE INDEX idx_creative_analysis_agency_client ON creative_analysis (agency_id, client_id);

-- 3. copy_variant
CREATE TABLE copy_variant (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    language            text            NOT NULL,
    primary_text        text            NOT NULL,
    headline            text            NOT NULL,
    description         text            NULL,
    cta                 text            NOT NULL,       -- SHOP_NOW | LEARN_MORE | ...
    status              text            NOT NULL,       -- DRAFT | APPROVED | ARCHIVED
    created_by          uuid            NOT NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_copy_variant PRIMARY KEY (id)
);

CREATE INDEX idx_copy_variant_agency_client_status ON copy_variant (agency_id, client_id, status);

-- 4. creative_package
CREATE TABLE creative_package (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    name                text            NOT NULL,
    objective           text            NOT NULL,       -- SALES | LEADS | ...
    status              text            NOT NULL,       -- DRAFT | IN_REVIEW | APPROVED | SCHEDULED | USED | ARCHIVED
    notes               text            NULL,
    created_by          uuid            NOT NULL,
    approved_by         uuid            NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    approved_at         timestamptz     NULL,

    CONSTRAINT pk_creative_package PRIMARY KEY (id),
    CONSTRAINT uq_creative_package_client_name UNIQUE (client_id, name)
);

CREATE INDEX idx_creative_package_agency_client_status ON creative_package (agency_id, client_id, status);

-- 5. creative_package_item
CREATE TABLE creative_package_item (
    id                  uuid            NOT NULL DEFAULT gen_random_uuid(),
    agency_id           uuid            NOT NULL,
    client_id           uuid            NOT NULL,
    package_id          uuid            NOT NULL,
    creative_asset_id   uuid            NOT NULL,
    copy_variant_id     uuid            NOT NULL,
    weight              integer         NOT NULL,
    created_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_creative_package_item PRIMARY KEY (id),
    CONSTRAINT fk_cpi_package FOREIGN KEY (package_id)
        REFERENCES creative_package (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cpi_asset FOREIGN KEY (creative_asset_id)
        REFERENCES creative_asset (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cpi_copy FOREIGN KEY (copy_variant_id)
        REFERENCES copy_variant (id) ON DELETE RESTRICT,
    CONSTRAINT uq_cpi_package_asset_copy UNIQUE (package_id, creative_asset_id, copy_variant_id)
);

CREATE INDEX idx_cpi_package ON creative_package_item (package_id);
