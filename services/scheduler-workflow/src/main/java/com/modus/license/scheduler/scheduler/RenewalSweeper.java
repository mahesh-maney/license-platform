package com.modus.license.scheduler.scheduler;

import com.modus.license.scheduler.config.ServicesProperties;
import com.modus.license.scheduler.workflow.SubscriptionLifecycleWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic reconciliation sweeper that ensures subscriptions approaching renewal
 * have an active {@link SubscriptionLifecycleWorkflow}.
 *
 * <p>This is a safety net: the primary trigger for starting workflows is the
 * {@code POST /api/v1/scheduler/subscription-lifecycle} endpoint called by the
 * subscription-plan service when a subscription is activated.
 *
 * <p>Only active when Temporal is enabled ({@link ConditionalOnBean}).
 */
@Component
@ConditionalOnBean(WorkflowClient.class)
public class RenewalSweeper {

    private static final Logger log = LoggerFactory.getLogger(RenewalSweeper.class);

    private final WorkflowClient workflowClient;
    private final ServicesProperties servicesProperties;

    public RenewalSweeper(WorkflowClient workflowClient, ServicesProperties servicesProperties) {
        this.workflowClient     = workflowClient;
        this.servicesProperties = servicesProperties;
    }

    /**
     * Runs on the configured cron (default: daily at midnight).
     *
     * <p>Calls the subscription-plan service to list subscriptions expiring
     * within the next 30 days, then verifies each has a running workflow.
     * If not, logs a warning — the subscription-plan service should have started one.
     */
    @Scheduled(cron = "${scheduler.renewal-sweep-cron}")
    public void sweep() {
        log.info("Renewal sweep started");
        try {
            // Integration point: call subscription-plan service
            // GET {services.subscription.url}/internal/subscriptions/expiring?withinDays=30
            // For each returned subscription, check that a workflow exists:
            //   workflowClient.newUntypedWorkflowStub("subscription-lifecycle-{subscriptionId}")
            //     .query(...) → if WorkflowNotFoundException → log warning / restart
            log.info("Renewal sweep: subscription service URL = {}",
                    servicesProperties.subscription().url());
            // TODO: integrate when subscription-plan service exposes /internal/subscriptions/expiring
        } catch (Exception e) {
            log.warn("Renewal sweep failed: {}", e.getMessage());
        }
    }

    /**
     * Checks whether a lifecycle workflow is currently running for the given subscription.
     */
    public boolean isWorkflowRunning(String subscriptionId) {
        try {
            workflowClient.newUntypedWorkflowStub("subscription-lifecycle-" + subscriptionId)
                    .query("__stack_trace", String.class);
            return true;
        } catch (WorkflowNotFoundException e) {
            return false;
        }
    }
}
