package com.modus.license.scheduler.workflow.model;

import java.time.Instant;

/**
 * Input data for {@link com.modus.license.scheduler.workflow.SubscriptionLifecycleWorkflow}.
 *
 * Carries all subscription fields needed for renewal reminder and expiry events
 * so workflow activities can build complete Avro events without calling back
 * to the subscription service.
 */
public record SubscriptionLifecycleInput(
        String  tenantId,
        String  subscriptionId,
        String  planId,
        String  planTier,
        String  licenseType,
        String  billingCycle,
        Integer seatLimit,
        Instant startDate,
        Instant endDate,          // null = evergreen
        int     gracePeriodDays
) {}
