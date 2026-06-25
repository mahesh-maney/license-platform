package com.modus.license.core.domain.enums;

public enum EntitlementStatus {

    /** Entitlement is valid and in use. */
    ACTIVE,

    /** Entitlement validity period has passed. */
    EXPIRED,

    /** Entitlement has been administratively suspended. */
    SUSPENDED,

    /** Entitlement was explicitly revoked before expiry. */
    REVOKED,

    /** Entitlement is scheduled to start in the future. */
    PENDING;

    public boolean isUsable() {
        return this == ACTIVE;
    }
}
