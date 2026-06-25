package com.modus.license.core.domain.enums;

public enum LicenseType {

    /** A specific named user is assigned a license seat. */
    NAMED_USER,

    /** A fixed pool of concurrent sessions allowed at the same time. */
    CONCURRENT,

    /** Unlimited users within a single site/organisation. */
    SITE,

    /** Usage is metered and billed periodically based on consumption. */
    METERED_SUBSCRIPTION;
}
