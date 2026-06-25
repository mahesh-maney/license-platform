package com.modus.license.scheduler.scheduler;

import com.modus.license.scheduler.config.ServicesProperties;
import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic reconciliation sweeper for subscriptions that have passed their end date
 * but whose expiry workflows may not have fired (e.g., Temporal downtime).
 *
 * <p>Runs hourly by default. The actual expiry is driven by the Temporal
 * {@link com.modus.license.scheduler.workflow.SubscriptionLifecycleWorkflow} timer,
 * so this sweeper is a safety net only.
 */
@Component
@ConditionalOnBean(WorkflowClient.class)
public class ExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpirySweeper.class);

    private final WorkflowClient workflowClient;
    private final ServicesProperties servicesProperties;

    public ExpirySweeper(WorkflowClient workflowClient, ServicesProperties servicesProperties) {
        this.workflowClient     = workflowClient;
        this.servicesProperties = servicesProperties;
    }

    /**
     * Runs on the configured cron (default: hourly).
     *
     * <p>Integration point: calls subscription-plan service for subscriptions
     * whose end date has passed but status is still ACTIVE. For each, a
     * {@link com.modus.license.scheduler.workflow.SubscriptionLifecycleWorkflow}
     * should already be handling expiry via its internal timer. This sweep
     * serves as a reconciliation catch-up.
     */
    @Scheduled(cron = "${scheduler.expiry-sweep-cron}")
    public void sweep() {
        log.info("Expiry sweep started");
        try {
            // Integration point: call subscription-plan service
            // GET {services.subscription.url}/internal/subscriptions/overdue
            // For each overdue subscription, check workflow status and trigger expiry if needed
            log.info("Expiry sweep: subscription service URL = {}",
                    servicesProperties.subscription().url());
            // TODO: integrate when subscription-plan service exposes /internal/subscriptions/overdue
        } catch (Exception e) {
            log.warn("Expiry sweep failed: {}", e.getMessage());
        }
    }
}
