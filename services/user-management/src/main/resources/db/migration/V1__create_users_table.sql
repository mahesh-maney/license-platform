-- ============================================================
-- V1: Create users and user_roles tables
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    status              VARCHAR(30)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(36),
    last_modified_by    VARCHAR(36),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_users             PRIMARY KEY (id),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT chk_user_status      CHECK (status IN ('ACTIVE','PENDING_VERIFICATION','INACTIVE'))
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID        NOT NULL,
    role    VARCHAR(100) NOT NULL,

    CONSTRAINT pk_user_roles        PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_status    ON users (tenant_id, status);
