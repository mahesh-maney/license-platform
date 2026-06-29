package com.modus.license.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.entitlement.consumer.FeatureEventConsumer;
import com.modus.license.entitlement.consumer.SubscriptionEventConsumer;
import com.modus.license.entitlement.consumer.TenantEventConsumer;
import com.modus.license.entitlement.domain.cache.EntitlementCacheService;
import com.modus.license.entitlement.domain.event.EntitlementEventPublisher;
import com.modus.license.entitlement.domain.repository.EntitlementRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Entitlement service.
 *
 * Covers /api/v1/entitlements (EntitlementController) end-to-end against a
 * Testcontainers PostgreSQL instance.
 *
 * {@link EntitlementEventPublisher} is mocked to avoid needing a Kafka broker.
 * {@link EntitlementCacheService} is mocked to avoid needing a Redis instance
 * and to prevent stale cache state from interfering with transaction rollbacks.
 * Kafka consumers are mocked to prevent @KafkaListener registration.
 * Each test runs inside a transaction that rolls back automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class EntitlementIntegrationTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                PostgresTestContainer::getJdbcUrl);
        registry.add("spring.datasource.username",           PostgresTestContainer::getUsername);
        registry.add("spring.datasource.password",           PostgresTestContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled",                () -> "true");
        registry.add("spring.flyway.baseline-on-migrate",   () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",       () -> "validate");
        // Redis not needed — EntitlementCacheService is mocked
        registry.add("spring.data.redis.host",        () -> "localhost");
        registry.add("spring.data.redis.port",        () -> "6379");
        registry.add("spring.data.redis.password",    () -> "test_redis_pass");
        registry.add("spring.data.redis.ssl.enabled", () -> "false");
    }

    /** Avoids needing a real Kafka broker. */
    @MockBean EntitlementEventPublisher   eventPublisher;
    /** Avoids Redis and prevents stale cache cross-contamination between tests. */
    @MockBean EntitlementCacheService     cacheService;
    /** Prevents @KafkaListener registration for each consumer. */
    @MockBean SubscriptionEventConsumer   subscriptionEventConsumer;
    @MockBean TenantEventConsumer         tenantEventConsumer;
    @MockBean FeatureEventConsumer        featureEventConsumer;

    @Autowired MockMvc              mockMvc;
    @Autowired ObjectMapper         objectMapper;
    @Autowired EntitlementRepository entitlementRepository;

    static final String TENANT_ID =
            TenantContextTestHelper.DEFAULT_TENANT_ID.value().toString();
    static final String USER_ID =
            TenantContextTestHelper.DEFAULT_USER_ID.value().toString();

    // ── Security helpers ──────────────────────────────────────────────────────

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                .jwt(j -> j.subject(USER_ID)
                           .claim("tenant_id", TENANT_ID)
                           .claim("roles", java.util.List.of("PLATFORM_ADMIN")));
    }

    // ── Request body helpers ──────────────────────────────────────────────────

    private String entitlementBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "tenantId",       TENANT_ID,
                "subscriptionId", UUID.randomUUID().toString(),
                "planId",         UUID.randomUUID().toString(),
                "planTier",       "PROFESSIONAL",
                "licenseType",    "NAMED_USER",
                "seatLimit",      50,
                "featureKeys",    java.util.List.of("feature.read", "feature.export"),
                "startDate",      Instant.now().toString()
        ));
    }

    /** Grants an entitlement via API and returns its ID. */
    private String grantEntitlement() throws Exception {
        // Cache service returns empty on get (cache miss → DB path)
        when(cacheService.get(any())).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(post("/api/v1/entitlements")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entitlementBody()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    // ── POST /api/v1/entitlements ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /entitlements → 201 Created with status ACTIVE")
    void grantEntitlement_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/entitlements")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entitlementBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.planTier").value("PROFESSIONAL"));
    }

    @Test
    @DisplayName("POST /entitlements → 400 when required fields missing")
    void grantEntitlement_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/entitlements")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatLimit", 10))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /entitlements → 401 when unauthenticated")
    void grantEntitlement_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/entitlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entitlementBody()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/entitlements/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("GET /entitlements/{id} → 200 for existing entitlement")
    void getById_found() throws Exception {
        String id = grantEntitlement();

        mockMvc.perform(get("/api/v1/entitlements/{id}", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("GET /entitlements/{id} → 404 for non-existent ID")
    void getById_notFound() throws Exception {
        when(cacheService.get(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/entitlements/{id}", UUID.randomUUID())
                        .with(platformAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/entitlements ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /entitlements → 200 returns paginated list for current tenant")
    void listEntitlements_returns200() throws Exception {
        grantEntitlement();

        mockMvc.perform(get("/api/v1/entitlements")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── GET /api/v1/entitlements/active ───────────────────────────────────────

    @Test
    @DisplayName("GET /entitlements/active → 200 returns ACTIVE entitlement for tenant")
    void getActive_found() throws Exception {
        grantEntitlement();

        // Simulate cache miss so the service falls back to DB
        when(cacheService.get(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/entitlements/active")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID));
    }

    @Test
    @DisplayName("GET /entitlements/active → 404 when no active entitlement exists")
    void getActive_notFound() throws Exception {
        when(cacheService.get(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/entitlements/active")
                        .with(platformAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/v1/entitlements/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("PUT /entitlements/{id} → 200 updates seatLimit and featureKeys")
    void updateEntitlement_returns200() throws Exception {
        String id = grantEntitlement();

        mockMvc.perform(put("/api/v1/entitlements/{id}", id)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "seatLimit",  100,
                                "featureKeys", java.util.List.of("feature.admin")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatLimit").value(100));
    }

    // ── POST /api/v1/entitlements/{id}/revoke ────────────────────────────────

    @Test
    @DisplayName("POST /entitlements/{id}/revoke → 200 sets status REVOKED")
    void revokeEntitlement_returns200() throws Exception {
        String id = grantEntitlement();

        mockMvc.perform(post("/api/v1/entitlements/{id}/revoke", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    // ── POST /api/v1/entitlements/{id}/suspend ───────────────────────────────

    @Test
    @DisplayName("POST /entitlements/{id}/suspend → 200 sets status SUSPENDED")
    void suspendEntitlement_returns200() throws Exception {
        String id = grantEntitlement();

        mockMvc.perform(post("/api/v1/entitlements/{id}/suspend", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }
}
