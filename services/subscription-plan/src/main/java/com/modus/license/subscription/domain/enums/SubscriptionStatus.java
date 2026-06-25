package com.modus.license.subscription.domain.enums;

public enum SubscriptionStatus {

    /** Subscription is active and enforced. */
    ACTIVE,

    /** In trial period — functionality available but time-limited. */
    TRIAL,

    /** Temporarily suspended, e.g. due to payment failure. */
    SUSPENDED,

    /** Cancelled by tenant or admin. */
    CANCELLED,

    /** End date passed and was not renewed. */
    EXPIRED;

    public boolean isUsable() {
        return this == ACTIVE || this == TRIAL;
    }
}
