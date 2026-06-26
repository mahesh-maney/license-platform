package com.modus.license.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.reporting.consumer.EntitlementEventConsumer;
import com.modus.license.reporting.consumer.SubscriptionEventConsumer;
import com.modus.license.reporting.consumer.UsageEventConsumer;
import com.modus.license.test.containers.PostgresTestContainer;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Reporting Analytics service.
 *
 * Covers /api/v1/reports (ReportingController) end-to-end against
 * Testcontainers PostgreSQL.
 *
 * All three Kafka consumers are mocked to avoid needing a real broker.
 * No Azure BlobServiceClient is registered, so {@link com.modus.license.reporting.service.ReportExportService}
 * is absent from the context — export endpoint returns 503.
 *
 * Each test runs inside a transaction that rolls back automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class ReportingIntegrationTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                PostgresTestContainer::getJdbcUrl);
        registry.add("spring.datasource.username",           PostgresTestContainer::getUsername);
        registry.add("spring.datasource.password",           PostgresTestContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled",                () -> "true");
        registry.add("spring.flyway.baseline-on-migrate",   () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",       () -> "validate");
    }

    /** Mock consumers to prevent @KafkaListener from trying to connect to Kafka. */
    @MockBean UsageEventConsumer        usageEventConsumer;
    @MockBean EntitlementEventConsumer  entitlementEventConsumer;
    @MockBean SubscriptionEventConsumer subscriptionEventConsumer;

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    static final String TENANT_ID =
            TenantContextTestHelper.DEFAULT_TENANT_ID.value().toString();
    static final String USER_ID   =
            TenantContextTestHelper.DEFAULT_USER_ID.value().toString();

    // ── Security helper ───────────────────────────────────────────────────────

    private static org.springframework.test.web.servlet.request.RequestPostProcessor tenantAdmin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                             new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
                .jwt(j -> j.subject(USER_ID)
                           .claim("tenant_id", TENANT_ID)
                           .claim("roles", List.of("PLATFORM_ADMIN", "TENANT_ADMIN")));
    }

    // ── GET /api/v1/reports/usage ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/usage → 200 with empty items (no data seeded)")
    void getUsageMetrics_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/reports/usage")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /reports/usage → 401 when unauthenticated")
    void getUsageMetrics_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /reports/usage?featureKey=X&metricName=Y → 200 with filter params")
    void getUsageMetrics_withFilters_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/reports/usage")
                        .param("featureKey", "EXPORT_PDF")
                        .param("metricName", "API_CALLS")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ── GET /api/v1/reports/entitlements ─────────────────────────────────────

    @Test
    @DisplayName("GET /reports/entitlements → 200 with empty items")
    void getEntitlements_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/reports/entitlements")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ── GET /api/v1/reports/subscriptions ────────────────────────────────────

    @Test
    @DisplayName("GET /reports/subscriptions → 200 with empty items")
    void getSubscriptions_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/reports/subscriptions")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ── POST /api/v1/reports/export ───────────────────────────────────────────

    @Test
    @DisplayName("POST /reports/export → 503 when Azure Blob Storage not configured")
    void export_noAzureBlob_returns503() throws Exception {
        mockMvc.perform(post("/api/v1/reports/export")
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reportType", "USAGE"
                        ))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /reports/export → 400 when reportType is blank")
    void export_blankReportType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/reports/export")
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reportType", ""
                        ))))
                .andExpect(status().isBadRequest());
    }
}
