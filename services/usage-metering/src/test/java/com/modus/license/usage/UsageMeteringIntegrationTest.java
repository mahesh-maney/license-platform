package com.modus.license.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.test.context.TenantContextTestHelper;
import com.modus.license.usage.consumer.EnforcementEventConsumer;
import com.modus.license.usage.consumer.SessionEventConsumer;
import com.modus.license.usage.domain.event.UsageEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Usage Metering service.
 *
 * This service has no database — all state is published to Kafka as Avro events.
 * {@link UsageEventPublisher}, {@link EnforcementEventConsumer}, and
 * {@link SessionEventConsumer} are mocked to avoid needing a real Kafka broker.
 *
 * Tests cover the single HTTP endpoint: POST /api/v1/usage/record.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsageMeteringIntegrationTest {

    /** Mocked to prevent KafkaTemplate from connecting to a real broker. */
    @MockBean UsageEventPublisher     usageEventPublisher;
    /** Mocked to prevent @KafkaListener from registering (no Kafka broker in tests). */
    @MockBean EnforcementEventConsumer enforcementEventConsumer;
    @MockBean SessionEventConsumer     sessionEventConsumer;

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    static final String TENANT_ID =
            TenantContextTestHelper.DEFAULT_TENANT_ID.value().toString();
    static final String USER_ID   =
            TenantContextTestHelper.DEFAULT_USER_ID.value().toString();

    // ── Security helper ───────────────────────────────────────────────────────

    private static org.springframework.test.web.servlet.request.RequestPostProcessor anyUser() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("TENANT_USER"))
                .jwt(j -> j.subject(USER_ID)
                           .claim("tenant_id", TENANT_ID)
                           .claim("roles", List.of("TENANT_USER")));
    }

    // ── POST /api/v1/usage/record ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /usage/record → 202 Accepted with usage details")
    void recordUsage_returns202() throws Exception {
        mockMvc.perform(post("/api/v1/usage/record")
                        .with(anyUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId",     UUID.randomUUID(),
                                "featureKey", "EXPORT_PDF",
                                "metricName", "API_CALLS",
                                "quantity",   1.0,
                                "unit",       "CALLS"
                        ))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.featureKey").value("EXPORT_PDF"))
                .andExpect(jsonPath("$.data.metricName").value("API_CALLS"))
                .andExpect(jsonPath("$.data.unit").value("CALLS"))
                .andExpect(jsonPath("$.data.usageId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /usage/record with null userId → 202 Accepted (userId optional)")
    void recordUsage_nullUserId_returns202() throws Exception {
        mockMvc.perform(post("/api/v1/usage/record")
                        .with(anyUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "featureKey", "BATCH_EXPORT",
                                "metricName", "EXPORTS",
                                "quantity",   3.0,
                                "unit",       "FILES"
                        ))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.featureKey").value("BATCH_EXPORT"));
    }

    @Test
    @DisplayName("POST /usage/record → 400 when required fields are missing")
    void recordUsage_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/usage/record")
                        .with(anyUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "quantity", 1.0
                                // missing featureKey, metricName, unit
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /usage/record → 401 when unauthenticated")
    void recordUsage_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "featureKey", "EXPORT_PDF",
                                "metricName", "API_CALLS",
                                "quantity",   1.0,
                                "unit",       "CALLS"
                        ))))
                .andExpect(status().isUnauthorized());
    }
}
