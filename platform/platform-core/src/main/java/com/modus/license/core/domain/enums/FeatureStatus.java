package com.modus.license.core.domain.enums;

public enum FeatureStatus {

    /** Feature is fully enabled for eligible tenants. */
    ENABLED,

    /** Feature is disabled globally or for the target tenant. */
    DISABLED,

    /** Feature is in beta — enabled only for opted-in tenants. */
    BETA,

    /** Feature has been deprecated and will be removed in a future release. */
    DEPRECATED;

    public boolean isAccessible() {
        return this == ENABLED || this == BETA;
    }
}
