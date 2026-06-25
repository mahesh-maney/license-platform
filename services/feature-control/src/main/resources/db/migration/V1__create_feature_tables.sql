-- ============================================================
-- V1: Create feature_definitions and tenant_feature_overrides tables
-- ============================================================

CREATE TABLE IF NOT EXISTS feature_definitions (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    feature_key         VARCHAR(100)    NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    description         VARCHAR(500),
    minimum_plan_tier   VARCHAR(20),
    status              VARCHAR(20)     NOT NULL,
    config_schema_json  TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    last_modified_by    VARCHAR(36),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_feature_definitions      PRIMARY KEY (id),
    CONSTRAINT uq_feature_key              UNIQUE (feature_key),
    CONSTRAINT chk_feature_status          CHECK (status IN ('ENABLED','DISABLED','BETA','DEPRECATED')),
    CONSTRAINT chk_feature_min_plan_tier   CHECK (minimum_plan_tier IS NULL
                                               OR minimum_plan_tier IN ('FREE','STARTER','PROFESSIONAL','ENTERPRISE','CUSTOM'))
);

CREATE TABLE IF NOT EXISTS tenant_feature_overrides (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    feature_id          UUID            NOT NULL,
    feature_key         VARCHAR(100)    NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    config_json         TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    last_modified_by    VARCHAR(36),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenant_feature_overrides      PRIMARY KEY (id),
    CONSTRAINT uq_tenant_feature                UNIQUE (tenant_id, feature_key),
    CONSTRAINT fk_tenant_feature_definition     FOREIGN KEY (feature_id) REFERENCES feature_definitions(id),
    CONSTRAINT chk_override_status              CHECK (status IN ('ENABLED','DISABLED','BETA','DEPRECATED'))
);

CREATE INDEX IF NOT EXISTS idx_feature_def_status       ON feature_definitions (status);
CREATE INDEX IF NOT EXISTS idx_feature_def_min_tier     ON feature_definitions (minimum_plan_tier);
CREATE INDEX IF NOT EXISTS idx_tfo_tenant_id            ON tenant_feature_overrides (tenant_id);
