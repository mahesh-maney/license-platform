-- Named-user license pools
CREATE TABLE named_licenses (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    version        BIGINT      NOT NULL DEFAULT 0,
    tenant_id      UUID        NOT NULL,
    plan_id        UUID        NOT NULL,
    entitlement_id UUID        NOT NULL,
    total_seats    INT         NOT NULL CHECK (total_seats > 0),
    used_seats     INT         NOT NULL DEFAULT 0 CHECK (used_seats >= 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(255),
    last_modified_by VARCHAR(255),
    CONSTRAINT uq_named_license_entitlement UNIQUE (tenant_id, entitlement_id),
    CONSTRAINT chk_used_seats_lte_total CHECK (used_seats <= total_seats)
);

CREATE INDEX idx_named_licenses_tenant_id ON named_licenses (tenant_id);

-- Individual seat assignments within a pool
CREATE TABLE seat_assignments (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    version    BIGINT      NOT NULL DEFAULT 0,
    license_id UUID        NOT NULL REFERENCES named_licenses (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    CONSTRAINT uq_seat_assignment_user UNIQUE (license_id, user_id)
);

CREATE INDEX idx_seat_assignments_license_id ON seat_assignments (license_id);
CREATE INDEX idx_seat_assignments_user_id    ON seat_assignments (user_id);
