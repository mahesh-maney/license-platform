-- ============================================================
-- V1: Create entitlements and entitlement_features tables
-- ============================================================

CREATE TABLE IF NOT EXISTS entitlements (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    subscription_id     UUID            NOT NULL,
    plan_id             UUID            NOT NULL,
    plan_tier           VARCHAR(20)     NOT NULL,
    license_type        VARCHAR(30)     NOT NULL,
    seat_limit          INT,
    status              VARCHAR(20)     NOT NULL,
    start_date          TIMESTAMPTZ     NOT NULL,
    end_date            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    last_modified_by    VARCHAR(36),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_entitlements              PRIMARY KEY (id),
    CONSTRAINT chk_entitlement_status       CHECK (status IN ('ACTIVE','EXPIRED','SUSPENDED','REVOKED','PENDING')),
    CONSTRAINT chk_entitlement_plan_tier    CHECK (plan_tier IN ('FREE','STARTER','PROFESSIONAL','ENTERPRISE','CUSTOM')),
    CONSTRAINT chk_entitlement_license_type CHECK (license_type IN ('NAMED_USER','CONCURRENT','SITE','METERED_SUBSCRIPTION'))
);

CREATE TABLE IF NOT EXISTS entitlement_features (
    entitlement_id  UUID            NOT NULL,
    feature_key     VARCHAR(150)    NOT NULL,

    CONSTRAINT pk_entitlement_features          PRIMARY KEY (entitlement_id, feature_key),
    CONSTRAINT fk_entitlement_features_ent      FOREIGN KEY (entitlement_id) REFERENCES entitlements(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_entitlements_tenant_id   ON entitlements (tenant_id);
CREATE INDEX IF NOT EXISTS idx_entitlements_sub_id      ON entitlements (subscription_id);
CREATE INDEX IF NOT EXISTS idx_entitlements_status      ON entitlements (tenant_id, status);
