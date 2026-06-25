package com.modus.license.scheduler.config;

import com.modus.license.scheduler.workflow.SubscriptionLifecycleWorkflowImpl;
import com.modus.license.scheduler.workflow.activity.SubscriptionActivitiesImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Temporal.io client, worker factory, and worker registration.
 *
 * <p>Set {@code temporal.enabled=false} to disable Temporal bootstrapping
 * (e.g., in unit tests or local development without a Temporal server).
 */
@Configuration
@ConditionalOnProperty(name = "temporal.enabled", havingValue = "true", matchIfMissing = true)
public class TemporalConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);

    @Bean
    public WorkflowServiceStubs workflowServiceStubs(TemporalProperties props) {
        return WorkflowServiceStubs.newInstance(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(props.serviceAddress())
                        .build()
        );
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs, TemporalProperties props) {
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(props.namespace())
                        .build()
        );
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    public Worker subscriptionWorker(WorkerFactory factory,
                                      TemporalProperties props,
                                      SubscriptionActivitiesImpl activities) {
        Worker worker = factory.newWorker(props.taskQueue(),
                WorkerOptions.newBuilder()
                        .setMaxConcurrentWorkflowTaskExecutionSize(props.worker().maxConcurrentWorkflows())
                        .setMaxConcurrentActivityExecutionSize(props.worker().maxConcurrentActivities())
                        .build()
        );
        worker.registerWorkflowImplementationTypes(SubscriptionLifecycleWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        log.info("Registered Temporal worker on task-queue={} namespace={}",
                props.taskQueue(), props.namespace());
        return worker;
    }

    /**
     * Starts the worker factory after all beans are ready.
     * Workers begin polling Temporal for workflow and activity tasks.
     */
    @Bean
    public ApplicationRunner temporalWorkerStarter(WorkerFactory factory) {
        return args -> {
            factory.start();
            log.info("Temporal WorkerFactory started");
        };
    }
}
