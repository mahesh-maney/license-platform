package com.modus.license.scheduler.api;

import com.modus.license.scheduler.api.dto.StartLifecycleRequest;
import com.modus.license.scheduler.config.SchedulerProperties;
import com.modus.license.scheduler.config.TemporalProperties;
import com.modus.license.scheduler.workflow.SubscriptionLifecycleWorkflow;
import com.modus.license.scheduler.workflow.model.SubscriptionLifecycleInput;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Admin API for managing subscription lifecycle workflows.
 *
 * <p>The primary client is the subscription-plan service (service-to-service),
 * which calls {@code POST /api/v1/scheduler/subscription-lifecycle}
 * whenever a subscription is activated or renewed.
 */
@RestController
@RequestMapping("/api/v1/scheduler")
@ConditionalOnBean(WorkflowClient.class)
public class SchedulerController {

    private static final Logger log = LoggerFactory.getLogger(SchedulerController.class);

    private static final String WORKFLOW_ID_PREFIX = "subscription-lifecycle-";

    private final WorkflowClient      workflowClient;
    private final TemporalProperties  temporalProps;
    private final SchedulerProperties schedulerProps;

    public SchedulerController(WorkflowClient workflowClient,
                                TemporalProperties temporalProps,
                                SchedulerProperties schedulerProps) {
        this.workflowClient   = workflowClient;
        this.temporalProps    = temporalProps;
        this.schedulerProps   = schedulerProps;
    }

    /**
     * POST /api/v1/scheduler/subscription-lifecycle
     *
     * Starts (or resumes, if already running) a lifecycle workflow for the given subscription.
     * Idempotent: if the workflow is already running with the same ID, a 409 is returned.
     */
    @PostMapping("/subscription-lifecycle")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SERVICE_ACCOUNT')")
    public ResponseEntity<Void> startLifecycle(@RequestBody @Valid StartLifecycleRequest req) {
        String workflowId = WORKFLOW_ID_PREFIX + req.subscriptionId();

        SubscriptionLifecycleInput input = new SubscriptionLifecycleInput(
                req.tenantId().toString(),
                req.subscriptionId(),
                req.planId(),
                req.planTier(),
                req.licenseType(),
                req.billingCycle(),
                req.seatLimit(),
                req.startDate(),
                req.endDate(),
                schedulerProps.gracePeriodDays()
        );

        try {
            SubscriptionLifecycleWorkflow stub = workflowClient.newWorkflowStub(
                    SubscriptionLifecycleWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setTaskQueue(temporalProps.taskQueue())
                            .build()
            );
            WorkflowClient.start(stub::run, input);
            log.info("Started lifecycle workflow: workflowId={} tenantId={}",
                    workflowId, req.tenantId());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (WorkflowExecutionAlreadyStarted e) {
            log.warn("Lifecycle workflow already running: workflowId={}", workflowId);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * POST /api/v1/scheduler/subscription-lifecycle/{subscriptionId}/renew
     *
     * Signals the running lifecycle workflow that the subscription has been renewed.
     */
    @PostMapping("/subscription-lifecycle/{subscriptionId}/renew")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SERVICE_ACCOUNT')")
    public ResponseEntity<Void> renewLifecycle(@PathVariable String subscriptionId) {
        String workflowId = WORKFLOW_ID_PREFIX + subscriptionId;
        try {
            SubscriptionLifecycleWorkflow stub = workflowClient.newWorkflowStub(
                    SubscriptionLifecycleWorkflow.class, workflowId);
            // Signal with renewed timestamp — the new endDate comes from the subscription service
            // In production, pass the newEndDate in the request body
            stub.renewed(Instant.now().plusSeconds(365L * 86_400)); // placeholder
            log.info("Sent renewal signal: workflowId={}", workflowId);
            return ResponseEntity.ok().build();
        } catch (WorkflowNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * DELETE /api/v1/scheduler/subscription-lifecycle/{subscriptionId}
     *
     * Sends a cancellation signal to stop tracking the subscription lifecycle.
     */
    @DeleteMapping("/subscription-lifecycle/{subscriptionId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SERVICE_ACCOUNT')")
    public ResponseEntity<Void> cancelLifecycle(@PathVariable String subscriptionId) {
        String workflowId = WORKFLOW_ID_PREFIX + subscriptionId;
        try {
            SubscriptionLifecycleWorkflow stub = workflowClient.newWorkflowStub(
                    SubscriptionLifecycleWorkflow.class, workflowId);
            stub.cancelled("Subscription cancelled via admin API");
            log.info("Sent cancellation signal: workflowId={}", workflowId);
            return ResponseEntity.noContent().build();
        } catch (WorkflowNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
