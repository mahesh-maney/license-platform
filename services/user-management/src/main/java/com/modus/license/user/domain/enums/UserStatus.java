package com.modus.license.user.domain.enums;

/**
 * Lifecycle status of a user account within a tenant.
 */
public enum UserStatus {

    /** Account is active — user can authenticate and use the platform. */
    ACTIVE,

    /** Email verification pending — user registered but has not verified yet. */
    PENDING_VERIFICATION,

    /** Account deactivated — user cannot authenticate. Soft-deleted state. */
    INACTIVE;

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
