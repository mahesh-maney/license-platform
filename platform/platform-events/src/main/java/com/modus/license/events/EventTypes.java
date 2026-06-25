package com.modus.license.events;

/**
 * String constants for all eventType discriminator values used across Avro schemas.
 *
 * Grouped as nested classes by domain. These values match what is stored
 * in EventMetadata.eventType and used by consumers to route/handle events.
 */
public final class EventTypes {

    private EventTypes() {}

    public static final class Tenant {
        private Tenant() {}
        public static final String CREATED        = "TENANT_CREATED";
        public static final String UPDATED        = "TENANT_UPDATED";
        public static final String SUSPENDED      = "TENANT_SUSPENDED";
        public static final String ACTIVATED      = "TENANT_ACTIVATED";
        public static final String DELETED        = "TENANT_DELETED";
        public static final String TRIAL_STARTED  = "TENANT_TRIAL_STARTED";
        public static final String TRIAL_EXPIRED  = "TENANT_TRIAL_EXPIRED";
    }

    public static final class Subscription {
        private Subscription() {}
        public static final String CREATED    = "SUBSCRIPTION_CREATED";
        public static final String ACTIVATED  = "SUBSCRIPTION_ACTIVATED";
        public static final String UPGRADED   = "SUBSCRIPTION_UPGRADED";
        public static final String DOWNGRADED = "SUBSCRIPTION_DOWNGRADED";
        public static final String CANCELLED  = "SUBSCRIPTION_CANCELLED";
        public static final String EXPIRED    = "SUBSCRIPTION_EXPIRED";
        public static final String RENEWED    = "SUBSCRIPTION_RENEWED";
        public static final String SUSPENDED  = "SUBSCRIPTION_SUSPENDED";
    }

    public static final class Entitlement {
        private Entitlement() {}
        public static final String GRANTED   = "ENTITLEMENT_GRANTED";
        public static final String UPDATED   = "ENTITLEMENT_UPDATED";
        public static final String EXPIRED   = "ENTITLEMENT_EXPIRED";
        public static final String SUSPENDED = "ENTITLEMENT_SUSPENDED";
        public static final String REVOKED   = "ENTITLEMENT_REVOKED";
        public static final String RENEWED   = "ENTITLEMENT_RENEWED";
    }

    public static final class Feature {
        private Feature() {}
        public static final String ENABLED      = "FEATURE_ENABLED";
        public static final String DISABLED     = "FEATURE_DISABLED";
        public static final String UPDATED      = "FEATURE_UPDATED";
        public static final String BETA_GRANTED = "FEATURE_BETA_GRANTED";
        public static final String BETA_REVOKED = "FEATURE_BETA_REVOKED";
        public static final String DEPRECATED   = "FEATURE_DEPRECATED";
    }

    public static final class Session {
        private Session() {}
        public static final String STARTED    = "SESSION_STARTED";
        public static final String HEARTBEAT  = "SESSION_HEARTBEAT";
        public static final String ENDED      = "SESSION_ENDED";
        public static final String EXPIRED    = "SESSION_EXPIRED";
        public static final String KILLED     = "SESSION_KILLED";
    }

    public static final class Enforcement {
        private Enforcement() {}
        public static final String ALLOWED = "ACCESS_ALLOWED";
        public static final String DENIED  = "ACCESS_DENIED";
    }

    public static final class NamedLicense {
        private NamedLicense() {}
        public static final String ASSIGNED           = "LICENSE_ASSIGNED";
        public static final String REVOKED            = "LICENSE_REVOKED";
        public static final String TRANSFERRED        = "LICENSE_TRANSFERRED";
        public static final String SEAT_LIMIT_CHANGED = "LICENSE_SEAT_LIMIT_CHANGED";
    }

    public static final class User {
        private User() {}
        public static final String CREATED      = "USER_CREATED";
        public static final String UPDATED      = "USER_UPDATED";
        public static final String DEACTIVATED  = "USER_DEACTIVATED";
        public static final String REACTIVATED  = "USER_REACTIVATED";
        public static final String ROLE_CHANGED = "USER_ROLE_CHANGED";
        public static final String DELETED      = "USER_DELETED";
    }

    public static final class Usage {
        private Usage() {}
        public static final String RECORDED          = "USAGE_RECORDED";
        public static final String AGGREGATED        = "USAGE_AGGREGATED";
        public static final String THRESHOLD_REACHED = "USAGE_THRESHOLD_REACHED";
        public static final String LIMIT_EXCEEDED    = "USAGE_LIMIT_EXCEEDED";
    }

    public static final class Audit {
        private Audit() {}
        public static final String RECORDED = "AUDIT_RECORDED";
    }

    public static final class Notification {
        private Notification() {}
        public static final String EMAIL_REQUESTED   = "EMAIL_REQUESTED";
        public static final String WEBHOOK_REQUESTED = "WEBHOOK_REQUESTED";
        public static final String SENT              = "NOTIFICATION_SENT";
        public static final String FAILED            = "NOTIFICATION_FAILED";
        public static final String RETRYING          = "NOTIFICATION_RETRYING";
    }

    public static final class Billing {
        private Billing() {}
        public static final String INVOICE_CREATED         = "INVOICE_CREATED";
        public static final String PAYMENT_RECEIVED        = "PAYMENT_RECEIVED";
        public static final String PAYMENT_FAILED          = "PAYMENT_FAILED";
        public static final String PAYMENT_RETRY_SCHEDULED = "PAYMENT_RETRY_SCHEDULED";
        public static final String BILLING_STARTED         = "SUBSCRIPTION_BILLING_STARTED";
        public static final String USAGE_REPORTED          = "BILLING_USAGE_REPORTED";
    }
}
