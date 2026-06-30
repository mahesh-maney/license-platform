package com.modus.license.scheduler.scheduler;

import com.modus.license.scheduler.config.ServicesProperties;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpirySweeper")
class ExpirySweeperTest {

    @Mock WorkflowClient workflowClient;
    @Mock ServicesProperties servicesProperties;

    ExpirySweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new ExpirySweeper(workflowClient, servicesProperties);
    }

    @Test
    @DisplayName("sweep → completes without exception")
    void sweep_noException() {
        ServicesProperties.Downstream downstream = new ServicesProperties.Downstream("http://localhost:8082");
        when(servicesProperties.subscription()).thenReturn(downstream);

        // sweep() is a reconciliation stub — must not throw
        sweeper.sweep();
    }

    @Test
    @DisplayName("sweep → handles exception gracefully and logs warning")
    void sweep_handlesException() {
        when(servicesProperties.subscription())
                .thenThrow(new RuntimeException("config error"));

        // must NOT propagate — internal try/catch catches and logs
        sweeper.sweep();
    }
}
