package com.modus.license.scheduler.api;

import com.modus.license.scheduler.api.dto.StartLifecycleRequest;
import com.modus.license.scheduler.config.SchedulerProperties;
import com.modus.license.scheduler.config.TemporalProperties;
import com.modus.license.scheduler.workflow.SubscriptionLifecycleWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.workflow.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerController")
class SchedulerControllerTest {

    @Mock WorkflowClient workflowClient;

    SchedulerController controller;

    static final UUID   TENANT_ID       = UUID.randomUUID();
    static final String SUBSCRIPTION_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        TemporalProperties temporalProps = new TemporalProperties(
                "localhost:7233", "modus-license", "modus-license-tasks",
                new TemporalProperties.Worker(10, 5));
        SchedulerProperties schedulerProps = new SchedulerProperties(
                "0 0 * * *", "0 * * * *", 7);
        controller = new SchedulerController(workflowClient, temporalProps, schedulerProps);
    }

    private StartLifecycleRequest validRequest() {
        return new StartLifecycleRequest(
                TENANT_ID, SUBSCRIPTION_ID,
                "plan-1", "ENTERPRISE", "NAMED_USER",
                "ANNUAL", 50,
                Instant.now(), Instant.now().plusSeconds(86_400 * 365)
        );
    }

    // ── startLifecycle ────────────────────────────────────────────────────────

    @Test
    @DisplayName("startLifecycle → happy path → 201 CREATED")
    void startLifecycle_happyPath_returns201() {
        SubscriptionLifecycleWorkflow stub = mock(SubscriptionLifecycleWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(SubscriptionLifecycleWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(stub);

        try (MockedStatic<WorkflowClient> staticMock = mockStatic(WorkflowClient.class)) {
            staticMock.when(() -> WorkflowClient.start(any(Functions.Proc1.class), any())).thenAnswer(inv -> null);

            ResponseEntity<Void> response = controller.startLifecycle(validRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Test
    @DisplayName("startLifecycle → already running → 409 CONFLICT")
    void startLifecycle_alreadyStarted_returns409() {
        SubscriptionLifecycleWorkflow stub = mock(SubscriptionLifecycleWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(SubscriptionLifecycleWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(stub);

        try (MockedStatic<WorkflowClient> staticMock = mockStatic(WorkflowClient.class)) {
            staticMock.when(() -> WorkflowClient.start(any(Functions.Proc1.class), any()))
                      .thenThrow(mock(WorkflowExecutionAlreadyStarted.class));

            ResponseEntity<Void> response = controller.startLifecycle(validRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ── renewLifecycle ────────────────────────────────────────────────────────

    @Test
    @DisplayName("renewLifecycle → workflow found → 200 OK, renewed signal sent")
    void renewLifecycle_found_returns200() {
        SubscriptionLifecycleWorkflow stub = mock(SubscriptionLifecycleWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(SubscriptionLifecycleWorkflow.class), anyString()))
                .thenReturn(stub);

        ResponseEntity<Void> response = controller.renewLifecycle(SUBSCRIPTION_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stub).renewed(any(Instant.class));
    }

    @Test
    @DisplayName("renewLifecycle → workflow not found → 404")
    void renewLifecycle_notFound_returns404() {
        when(workflowClient.newWorkflowStub(eq(SubscriptionLifecycleWorkflow.class), anyString()))
                .thenThrow(mock(WorkflowNotFoundException.class));

        ResponseEntity<Void> response = controller.renewLifecycle(SUBSCRIPTION_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── cancelLifecycle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelLifecycle → workflow found → 204 NO CONTENT, cancelled signal sent")
    void cancelLifecycle_found_returns204() {
        SubscriptionLifecycleWorkflow stub = mock(SubscriptionLifecycleWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(SubscriptionLifecycleWorkflow.class), anyString()))
                .thenReturn(stub);

        ResponseEntity<Void> response = controller.cancelLifecycle(SUBSCRIPTION_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(stub).cancelled(anyString());
    }

    @Test
    @DisplayName("cancelLifecycle → workflow not found → 404")
    void cancelLifecycle_notFound_returns404() {
        when(workflowClient.newWorkflowStub(eq(SubscriptionLifecycleWorkflow.class), anyString()))
                .thenThrow(mock(WorkflowNotFoundException.class));

        ResponseEntity<Void> response = controller.cancelLifecycle(SUBSCRIPTION_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
