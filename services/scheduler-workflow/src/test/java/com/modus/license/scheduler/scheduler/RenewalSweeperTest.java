package com.modus.license.scheduler.scheduler;

import com.modus.license.scheduler.config.ServicesProperties;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RenewalSweeper")
class RenewalSweeperTest {

    @Mock WorkflowClient workflowClient;
    @Mock WorkflowStub workflowStub;
    @Mock ServicesProperties servicesProperties;

    RenewalSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new RenewalSweeper(workflowClient, servicesProperties);
    }

    @Test
    @DisplayName("isWorkflowRunning → true when workflow exists and responds to query")
    void isWorkflowRunning_true() {
        String subscriptionId = UUID.randomUUID().toString();
        when(workflowClient.newUntypedWorkflowStub(anyString())).thenReturn(workflowStub);
        when(workflowStub.query(anyString(), any(Class.class))).thenReturn("some trace");

        assertThat(sweeper.isWorkflowRunning(subscriptionId)).isTrue();
    }

    @Test
    @DisplayName("isWorkflowRunning → false when workflow not found")
    void isWorkflowRunning_false() {
        String subscriptionId = UUID.randomUUID().toString();
        when(workflowClient.newUntypedWorkflowStub(anyString())).thenReturn(workflowStub);
        WorkflowNotFoundException notFound = org.mockito.Mockito.mock(WorkflowNotFoundException.class);
        doThrow(notFound).when(workflowStub).query(anyString(), any(Class.class));

        assertThat(sweeper.isWorkflowRunning(subscriptionId)).isFalse();
    }

    @Test
    @DisplayName("sweep → completes without exception even with no external calls")
    void sweep_noException() {
        ServicesProperties.Downstream downstream = new ServicesProperties.Downstream("http://localhost:8082");
        when(servicesProperties.subscription()).thenReturn(downstream);

        // sweep() is a stub that logs and returns — must not throw
        sweeper.sweep();
    }
}
