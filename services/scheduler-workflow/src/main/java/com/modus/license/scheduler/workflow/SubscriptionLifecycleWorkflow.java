package com.modus.license.scheduler.workflow;

import com.modus.license.scheduler.workflow.model.SubscriptionLifecycleInput;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.time.Instant;

/**
 * Temporal workflow that manages the full lifecycle of a subscription:
 * <ol>
 *   <li>Sends renewal reminder notifications at configurable intervals before expiry.</li>
 *   <li>Marks the subscription as expired after the grace period.</li>
 *   <li>Responds to {@code cancel} and {@code renew} signals mid-flight.</li>
 * </ol>
 *
 * <p>Workflow ID convention: {@code subscription-lifecycle-{subscriptionId}}.
 */
@WorkflowInterface
public interface SubscriptionLifecycleWorkflow {

    /**
     * Main workflow entry point. Sleeps using Temporal durable timers
     * so it survives worker restarts.
     */
    @WorkflowMethod
    void run(SubscriptionLifecycleInput input);

    /**
     * Signal: subscription has been cancelled. Workflow terminates gracefully.
     */
    @SignalMethod
    void cancelled(String reason);

    /**
     * Signal: subscription has been renewed. Workflow restarts with the new end date.
     */
    @SignalMethod
    void renewed(Instant newEndDate);
}
