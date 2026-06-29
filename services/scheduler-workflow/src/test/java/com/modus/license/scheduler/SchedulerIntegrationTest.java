package com.modus.license.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack security smoke test for the Scheduler-Workflow service.
 *
 * {@code temporal.enabled: false} keeps the application context lightweight
 * (no Temporal server required). Because {@code SchedulerController} is
 * {@code @ConditionalOnBean(WorkflowClient.class)} it is not registered when
 * Temporal is disabled, so Spring Security fires before the dispatcher is
 * reached — 401 responses are therefore verifiable without the controller.
 *
 * Business-logic paths (403, 400, 200, 204, 404) are covered by
 * {@code SchedulerControllerTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchedulerIntegrationTest {

    @Autowired MockMvc mockMvc;

    static final String SUBSCRIPTION_ID = UUID.randomUUID().toString();

    // ── POST /subscription-lifecycle ─────────────────────────────────────────

    @Test
    @DisplayName("POST /subscription-lifecycle → 401 when unauthenticated")
    void start_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/scheduler/subscription-lifecycle"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /subscription-lifecycle/{id}/renew ───────────────────────────────

    @Test
    @DisplayName("POST /subscription-lifecycle/{id}/renew → 401 when unauthenticated")
    void renew_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/scheduler/subscription-lifecycle/{id}/renew", SUBSCRIPTION_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /subscription-lifecycle/{id} ───────────────────────────────────

    @Test
    @DisplayName("DELETE /subscription-lifecycle/{id} → 401 when unauthenticated")
    void cancel_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/scheduler/subscription-lifecycle/{id}", SUBSCRIPTION_ID))
                .andExpect(status().isUnauthorized());
    }
}
