package com.modus.license.core.domain.enums;

public enum TenantStatus {

    /** Tenant is fully active and licensed. */
    ACTIVE,

    /** Tenant is in a time-limited trial period. */
    TRIAL,

    /** Tenant has been provisioned but setup is incomplete. */
    PENDING_SETUP,

    /** Tenant has been administratively suspended (e.g. payment overdue). */
    SUSPENDED,

    /** Tenant is deactivated and no longer accessible. */
    INACTIVE;

    public boolean isOperational() {
        return this == ACTIVE || this == TRIAL;
    }
}
