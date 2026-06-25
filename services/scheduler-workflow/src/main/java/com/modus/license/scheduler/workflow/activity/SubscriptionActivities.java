package com.modus.license.scheduler.workflow.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.time.Instant;

/**
 * Temporal activity interface for subscription lifecycle side-effects.
 *
 * <p>Each method publishes a Kafka event so downstream services
 * (subscription-plan, notification) react accordingly.
 */
@ActivityInterface
public interface SubscriptionActivities {

    /**
     * Publishes a renewal reminder event for the given subscription.
     *
     * @param daysUntilExpiry how many days remain before the subscription expires
     */
    @ActivityMethod
    void sendRenewalReminder(String tenantId, String subscriptionId, int daysUntilExpiry);

    /**
     * Publishes a notification that the subscription has lapsed and is in the grace period.
     */
    @ActivityMethod
    void notifyExpiry(String tenantId, String subscriptionId);

    /**
     * Publishes {@code SUBSCRIPTION_EXPIRED} to {@code modus.subscription.events}.
     * The subscription-plan service consumes this and updates subscription status.
     */
    @ActivityMethod
    void expireSubscription(String tenantId, String subscriptionId,
                             String planId, String planTier, String licenseType,
                             String billingCycle, Integer seatLimit,
                             Instant startDate, Instant endDate);
}
