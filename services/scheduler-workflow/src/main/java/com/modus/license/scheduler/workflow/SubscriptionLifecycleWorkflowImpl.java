package com.modus.license.scheduler.workflow;

import com.modus.license.scheduler.workflow.activity.SubscriptionActivities;
import com.modus.license.scheduler.workflow.model.SubscriptionLifecycleInput;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;

/**
 * Implements the subscription lifecycle as a long-running Temporal workflow.
 *
 * <p>Durable timers ({@link Workflow#await}) survive worker restarts.
 * When a {@code renewed} signal arrives mid-sleep, the workflow calls
 * {@link Workflow#newContinueAsNewStub} to restart cleanly with the new end date,
 * preventing unbounded history growth.
 */
public class SubscriptionLifecycleWorkflowImpl implements SubscriptionLifecycleWorkflow {

    private static final Logger log = Workflow.getLogger(SubscriptionLifecycleWorkflowImpl.class);

    /** Days before expiry at which renewal reminders are sent. */
    private static final int[] WARN_DAYS = {30, 14, 7, 1};

    private final SubscriptionActivities activities = Workflow.newActivityStub(
            SubscriptionActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build()
    );

    private boolean cancelled = false;
    private Instant newEndDate = null;

    @Override
    public void run(SubscriptionLifecycleInput input) {
        Instant endDate = input.endDate();

        if (endDate == null) {
            // Evergreen subscription — wait indefinitely for a renewal or cancellation signal
            Workflow.await(() -> cancelled || newEndDate != null);
            if (newEndDate != null) {
                Workflow.newContinueAsNewStub(SubscriptionLifecycleWorkflow.class)
                        .run(withNewEndDate(input, newEndDate));
            }
            return;
        }

        // Send renewal reminders at each configured interval before expiry
        for (int days : WARN_DAYS) {
            Instant warnAt = endDate.minusSeconds((long) days * 86_400);
            long warnMs = warnAt.toEpochMilli();
            long nowMs  = Workflow.currentTimeMillis();

            if (warnMs > nowMs) {
                boolean signalled = Workflow.await(
                        Duration.ofMillis(warnMs - nowMs),
                        () -> cancelled || newEndDate != null
                );
                if (signalled) {
                    if (cancelled) return;
                    // Renewed — restart with the new end date to recalculate all timers
                    Workflow.newContinueAsNewStub(SubscriptionLifecycleWorkflow.class)
                            .run(withNewEndDate(input, newEndDate));
                    return;
                }
            }
            log.info("Sending renewal reminder: subscriptionId={} daysUntilExpiry={}",
                    input.subscriptionId(), days);
            activities.sendRenewalReminder(input.tenantId(), input.subscriptionId(), days);
        }

        // Sleep until the subscription end date
        long endMs = endDate.toEpochMilli();
        long nowMs  = Workflow.currentTimeMillis();
        if (endMs > nowMs) {
            boolean signalled = Workflow.await(
                    Duration.ofMillis(endMs - nowMs),
                    () -> cancelled || newEndDate != null
            );
            if (signalled) {
                if (cancelled) return;
                Workflow.newContinueAsNewStub(SubscriptionLifecycleWorkflow.class)
                        .run(withNewEndDate(input, newEndDate));
                return;
            }
        }

        // Subscription has lapsed — notify and start the grace period countdown
        log.info("Subscription lapsed, entering grace period: subscriptionId={} graceDays={}",
                input.subscriptionId(), input.gracePeriodDays());
        activities.notifyExpiry(input.tenantId(), input.subscriptionId());

        boolean renewedDuringGrace = Workflow.await(
                Duration.ofDays(input.gracePeriodDays()),
                () -> cancelled || newEndDate != null
        );

        if (renewedDuringGrace) {
            if (cancelled) return;
            log.info("Subscription renewed during grace period: subscriptionId={}", input.subscriptionId());
            Workflow.newContinueAsNewStub(SubscriptionLifecycleWorkflow.class)
                    .run(withNewEndDate(input, newEndDate));
            return;
        }

        // Grace period exhausted — expire the subscription
        log.info("Grace period exhausted, expiring subscription: subscriptionId={}", input.subscriptionId());
        activities.expireSubscription(
                input.tenantId(), input.subscriptionId(), input.planId(),
                input.planTier(), input.licenseType(), input.billingCycle(),
                input.seatLimit(), input.startDate(), endDate);
    }

    @Override
    public void cancelled(String reason) {
        log.info("Received cancellation signal: subscriptionId will stop tracking, reason={}", reason);
        this.cancelled = true;
    }

    @Override
    public void renewed(Instant newEnd) {
        log.info("Received renewal signal with newEndDate={}", newEnd);
        this.newEndDate = newEnd;
    }

    private static SubscriptionLifecycleInput withNewEndDate(SubscriptionLifecycleInput input,
                                                              Instant endDate) {
        return new SubscriptionLifecycleInput(
                input.tenantId(), input.subscriptionId(), input.planId(),
                input.planTier(), input.licenseType(), input.billingCycle(),
                input.seatLimit(), input.startDate(), endDate, input.gracePeriodDays());
    }
}
