-- Tracks the latest admin contact for each tenant.
-- Populated from TenantEvents; one row per tenant.
CREATE TABLE tenant_contacts (
    tenant_id   UUID         NOT NULL PRIMARY KEY,
    admin_email VARCHAR(255) NOT NULL,
    tenant_name VARCHAR(255),
    status      VARCHAR(50),
    plan_tier   VARCHAR(50),
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- Notification dispatch record: one row per notification attempt.
CREATE TABLE notifications (
    id                UUID        NOT NULL PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    channel           VARCHAR(50) NOT NULL,
    recipient_email   VARCHAR(255),
    subject           VARCHAR(255),
    payload_json      TEXT,
    webhook_url       TEXT,
    status            VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED','RETRYING')),
    retry_count       INT         NOT NULL DEFAULT 0,
    failure_reason    TEXT,
    sent_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(36),
    last_modified_by  VARCHAR(36),
    version           BIGINT      NOT NULL DEFAULT 0
);

-- Most-recent notifications per tenant
CREATE INDEX idx_notifications_tenant_time
    ON notifications (tenant_id, created_at DESC);

-- Quick status filtering
CREATE INDEX idx_notifications_status
    ON notifications (status, created_at DESC);

-- Filter by notification type
CREATE INDEX idx_notifications_type
    ON notifications (tenant_id, notification_type, created_at DESC);
