-- ============================================================
-- V1: Create tenants table
-- ============================================================

CREATE TABLE IF NOT EXISTS tenants (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    slug                VARCHAR(100)    NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    display_name        VARCHAR(255),
    admin_email         VARCHAR(255)    NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    plan_tier           VARCHAR(20),
    region              VARCHAR(50)     NOT NULL,
    trial_ends_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    last_modified_by    VARCHAR(36),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenants           PRIMARY KEY (id),
    CONSTRAINT uq_tenants_slug      UNIQUE (slug),
    CONSTRAINT chk_tenant_status    CHECK (status IN ('ACTIVE','TRIAL','PENDING_SETUP','SUSPENDED','INACTIVE')),
    CONSTRAINT chk_tenant_plan_tier CHECK (plan_tier IS NULL OR plan_tier IN ('FREE','STARTER','PROFESSIONAL','ENTERPRISE','CUSTOM'))
);

CREATE INDEX IF NOT EXISTS idx_tenants_status    ON tenants (status);
CREATE INDEX IF NOT EXISTS idx_tenants_region    ON tenants (region);
CREATE INDEX IF NOT EXISTS idx_tenants_plan_tier ON tenants (plan_tier);
