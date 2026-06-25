package com.modus.license.events;

/**
 * Canonical Kafka topic names shared across all services.
 *
 * Services should reference these constants rather than hard-coding topic strings.
 * Values are overridable via environment variables (see each service's application.yml).
 */
public final class TopicConstants {

    private TopicConstants() {}

    public static final String TENANT_EVENTS          = "modus.tenant.events";
    public static final String SUBSCRIPTION_EVENTS    = "modus.subscription.events";
    public static final String ENTITLEMENT_EVENTS     = "modus.entitlement.events";
    public static final String FEATURE_EVENTS         = "modus.feature.events";
    public static final String SESSION_EVENTS         = "modus.session.events";
    public static final String ENFORCEMENT_DECISIONS  = "modus.enforcement.decisions";
    public static final String NAMED_LICENSE_EVENTS   = "modus.named-license.events";
    public static final String USER_EVENTS            = "modus.user.events";
    public static final String USAGE_EVENTS           = "modus.usage.events";
    public static final String AUDIT_EVENTS           = "modus.audit.events";
    public static final String NOTIFICATION_EVENTS    = "modus.notification.events";
    public static final String BILLING_EVENTS         = "modus.billing.events";
}
