-- ============================================================
-- V1: Create subscription_plans, plan_features, subscriptions tables
-- ============================================================

CREATE TABLE IF NOT EXISTS subscription_plans (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500),
    plan_tier           VARCHAR(20)     NOT NULL,
    license_type        VARCHAR(30)     NOT NULL,
    max_seats           INT,
    billing_cycle       VARCHAR(10)     NOT NULL,
    price_in_cents      BIGINT          NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    last_modified_by    VARCHAR(36),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_subscription_plans        PRIMARY KEY (id),
    CONSTRAINT uq_subscription_plans_name   UNIQUE (name),
    CONSTRAINT chk_plan_tier                CHECK (plan_tier IN ('FREE','STARTER','PROFESSIONAL','ENTERPRISE','CUSTOM')),
    CONSTRAINT chk_license_type             CHECK (license_type IN ('NAMED_USER','CONCURRENT','SITE','METERED_SUBSCRIPTION')),
    CONSTRAINT chk_billing_cycle            CHECK (billing_cycle IN ('MONTHLY','ANNUAL','CUSTOM')),
    CONSTRAINT chk_price_non_negative       CHECK (price_in_cents >= 0)
);

CREATE TABLE IF NOT EXISTS plan_features (
    plan_id     UUID            NOT NULL,
    feature_key VARCHAR(150)    NOT NULL,

    CONSTRAINT pk_plan_features     PRIMARY KEY (plan_id, feature_key),
    CONSTRAINT fk_plan_features_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    plan_id             UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    seat_limit          INT,
    billing_cycle       VARCHAR(10)     NOT NULL,
    start_date          TIMESTAMPTZ     NOT NULL,
    end_date            TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    last_modified_by    VARCHAR(36),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_subscriptions             PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_plan        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    CONSTRAINT chk_subscription_status      CHECK (status IN ('ACTIVE','TRIAL','SUSPENDED','CANCELLED','EXPIRED')),
    CONSTRAINT chk_subscription_billing     CHECK (billing_cycle IN ('MONTHLY','ANNUAL','CUSTOM'))
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant_id ON subscriptions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status    ON subscriptions (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_plans_tier_active       ON subscription_plans (plan_tier, active);
