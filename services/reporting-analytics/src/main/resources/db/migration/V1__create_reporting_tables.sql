-- Pre-aggregated usage metrics per (tenant, feature, metric, time window).
-- Populated from USAGE_AGGREGATED Kafka events; upserted on every event.
CREATE TABLE usage_metrics (
    id                UUID             NOT NULL PRIMARY KEY,
    tenant_id         UUID             NOT NULL,
    feature_key       VARCHAR(255)     NOT NULL,
    metric_name       VARCHAR(100)     NOT NULL,
    unit              VARCHAR(50)      NOT NULL,
    window_start      TIMESTAMPTZ      NOT NULL,
    window_end        TIMESTAMPTZ      NOT NULL,
    total_quantity    DOUBLE PRECISION NOT NULL DEFAULT 0,
    event_count       BIGINT           NOT NULL DEFAULT 0,
    threshold_breaches INT             NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ      NOT NULL,
    updated_at        TIMESTAMPTZ      NOT NULL,
    created_by        VARCHAR(36),
    last_modified_by  VARCHAR(36),
    version           BIGINT           NOT NULL DEFAULT 0
);

-- Unique key for upsert logic
CREATE UNIQUE INDEX uq_usage_metrics_key
    ON usage_metrics (tenant_id, feature_key, metric_name, window_start, window_end);

-- Primary access: tenant usage over time
CREATE INDEX idx_usage_metrics_tenant_time
    ON usage_metrics (tenant_id, window_start DESC);

-- Filter by feature
CREATE INDEX idx_usage_metrics_feature
    ON usage_metrics (tenant_id, feature_key, window_start DESC);


-- Current-state snapshot of each entitlement.
-- Populated from EntitlementEvents; one row per entitlement, updated in-place.
CREATE TABLE entitlement_snapshots (
    entitlement_id  UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    subscription_id UUID         NOT NULL,
    plan_id         UUID         NOT NULL,
    plan_tier       VARCHAR(50)  NOT NULL,
    license_type    VARCHAR(50)  NOT NULL,
    seat_limit      INT,
    feature_keys    TEXT,
    status          VARCHAR(50)  NOT NULL,
    start_date      TIMESTAMPTZ  NOT NULL,
    end_date        TIMESTAMPTZ,
    recorded_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_entitlement_snapshots_tenant
    ON entitlement_snapshots (tenant_id, recorded_at DESC);

CREATE INDEX idx_entitlement_snapshots_status
    ON entitlement_snapshots (tenant_id, status);


-- Current-state snapshot of each subscription.
-- Populated from SubscriptionEvents; one row per subscription, updated in-place.
CREATE TABLE subscription_snapshots (
    subscription_id UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    plan_id         UUID         NOT NULL,
    plan_tier       VARCHAR(50)  NOT NULL,
    license_type    VARCHAR(50)  NOT NULL,
    billing_cycle   VARCHAR(50)  NOT NULL,
    seat_limit      INT,
    status          VARCHAR(50)  NOT NULL,
    start_date      TIMESTAMPTZ  NOT NULL,
    end_date        TIMESTAMPTZ,
    recorded_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_subscription_snapshots_tenant
    ON subscription_snapshots (tenant_id, recorded_at DESC);

CREATE INDEX idx_subscription_snapshots_status
    ON subscription_snapshots (tenant_id, status);
