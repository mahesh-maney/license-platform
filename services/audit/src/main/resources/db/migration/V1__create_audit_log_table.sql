-- Immutable audit trail. Records are written once; no UPDATE or DELETE is permitted
-- at the application level. The content_hash field provides tamper-evidence.

CREATE TABLE audit_logs (
    id              UUID         NOT NULL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    actor_id        VARCHAR(255) NOT NULL,
    actor_type      VARCHAR(50)  NOT NULL,
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     VARCHAR(255) NOT NULL,
    outcome         VARCHAR(50)  NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    service_name    VARCHAR(100) NOT NULL,
    ip_address      VARCHAR(50),
    user_agent      TEXT,
    request_id      VARCHAR(255),
    before_state    TEXT,
    after_state     TEXT,
    failure_reason  TEXT,
    recorded_at     TIMESTAMPTZ  NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL
);

-- Primary access pattern: most-recent logs for a tenant
CREATE INDEX idx_audit_logs_tenant_time
    ON audit_logs (tenant_id, recorded_at DESC);

-- Filter by actor (who did it?)
CREATE INDEX idx_audit_logs_actor
    ON audit_logs (tenant_id, actor_id, recorded_at DESC);

-- Filter by resource (what was changed?)
CREATE INDEX idx_audit_logs_resource
    ON audit_logs (tenant_id, resource_type, resource_id, recorded_at DESC);

-- Filter by outcome (all failures/denials)
CREATE INDEX idx_audit_logs_outcome
    ON audit_logs (tenant_id, outcome, recorded_at DESC);
