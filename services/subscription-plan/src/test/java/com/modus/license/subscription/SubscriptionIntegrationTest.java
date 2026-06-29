package com.modus.license.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.subscription.domain.event.SubscriptionEventPublisher;
import com.modus.license.subscription.domain.repository.SubscriptionPlanRepository;
import com.modus.license.subscription.domain.repository.SubscriptionRepository;
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
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Subscription Plan service.
 *
 * Covers /api/v1/plans (SubscriptionPlanController) and
 * /api/v1/subscriptions (SubscriptionController) end-to-end against a
 * Testcontainers PostgreSQL instance.
 *
 * {@link SubscriptionEventPublisher} is mocked to avoid needing a Kafka broker.
 * Each test runs inside a transaction that rolls back automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class SubscriptionIntegrationTest {

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

    /** Avoids needing a real Kafka broker. Event publishing verified in unit tests. */
    @MockBean SubscriptionEventPublisher eventPublisher;

    @Autowired MockMvc               mockMvc;
    @Autowired ObjectMapper          objectMapper;
    @Autowired SubscriptionPlanRepository planRepository;
    @Autowired SubscriptionRepository     subscriptionRepository;

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

    // ── Request body builders ─────────────────────────────────────────────────

    private String planBody(String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name",         name,
                "description",  "Test plan",
                "tier",         "PROFESSIONAL",
                "licenseType",  "NAMED_USER",
                "maxSeats",     50,
                "billingCycle", "MONTHLY",
                "priceInCents", 4999,
                "features",     java.util.List.of("feature.export", "feature.read")
        ));
    }

    private String subscriptionBody(String planId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "planId",       planId,
                "billingCycle", "MONTHLY",
                "startDate",    Instant.now().toString()
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a plan via API and returns its ID string. */
    private String createPlan(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/plans")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    /** Creates a subscription for the default tenant and returns its ID string. */
    private String createSubscription(String planId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/subscriptions")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionBody(planId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    // ── POST /api/v1/plans ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /plans → 201 Created with correct payload")
    void createPlan_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Starter Monthly")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Starter Monthly"))
                .andExpect(jsonPath("$.data.tier").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("POST /plans → 400 when required fields missing")
    void createPlan_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Incomplete"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /plans → 409 when plan name already exists")
    void createPlan_duplicateName_returns409() throws Exception {
        createPlan("Dup Plan");

        mockMvc.perform(post("/api/v1/plans")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Dup Plan")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /plans → 401 when unauthenticated")
    void createPlan_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("Unauth Plan")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/plans/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /plans/{id} → 200 for existing plan")
    void getPlan_found() throws Exception {
        String planId = createPlan("Get Test Plan");

        mockMvc.perform(get("/api/v1/plans/{id}", planId)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId));
    }

    @Test
    @DisplayName("GET /plans/{id} → 404 for non-existent ID")
    void getPlan_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{id}", UUID.randomUUID())
                        .with(platformAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/plans ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /plans → 200 returns plan list")
    void listPlans_returns200() throws Exception {
        createPlan("List Plan A");
        createPlan("List Plan B");

        mockMvc.perform(get("/api/v1/plans")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /plans?activeOnly=true → 200 filters active plans")
    void listPlans_activeOnly() throws Exception {
        createPlan("Active Plan");

        mockMvc.perform(get("/api/v1/plans").param("activeOnly", "true")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── GET /api/v1/plans/by-tier/{tier} ──────────────────────────────────────

    @Test
    @DisplayName("GET /plans/by-tier/PROFESSIONAL → 200 returns matching plans")
    void listByTier_returns200() throws Exception {
        createPlan("Pro Tier Plan");

        mockMvc.perform(get("/api/v1/plans/by-tier/PROFESSIONAL")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── PATCH /api/v1/plans/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /plans/{id} → 200 updates description and priceInCents")
    void updatePlan_returns200() throws Exception {
        String planId = createPlan("Update Plan");

        mockMvc.perform(patch("/api/v1/plans/{id}", planId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description",  "Updated description",
                                "priceInCents", 5999
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.priceInCents").value(5999));
    }

    // ── POST /api/v1/plans/{id}/deactivate ───────────────────────────────────

    @Test
    @DisplayName("POST /plans/{id}/deactivate → 200 sets active=false")
    void deactivatePlan_returns200() throws Exception {
        String planId = createPlan("Deactivate Plan");

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", planId)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    // ── POST /api/v1/subscriptions ────────────────────────────────────────────

    @Test
    @DisplayName("POST /subscriptions → 201 Created with status ACTIVE")
    void createSubscription_returns201() throws Exception {
        String planId = createPlan("Sub Create Plan");

        mockMvc.perform(post("/api/v1/subscriptions")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionBody(planId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID));
    }

    @Test
    @DisplayName("POST /subscriptions → 409 when tenant already has an active subscription")
    void createSubscription_duplicate_returns409() throws Exception {
        String planId = createPlan("Sub Conflict Plan");
        createSubscription(planId);

        mockMvc.perform(post("/api/v1/subscriptions")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionBody(planId)))
                .andExpect(status().isConflict());
    }

    // ── GET /api/v1/subscriptions/{id} ────────────────────────────────────────

    @Test
    @DisplayName("GET /subscriptions/{id} → 200 for existing subscription")
    void getSubscription_found() throws Exception {
        String planId = createPlan("Get Sub Plan");
        String subId  = createSubscription(planId);

        mockMvc.perform(get("/api/v1/subscriptions/{id}", subId)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(subId));
    }

    // ── GET /api/v1/subscriptions/active ──────────────────────────────────────

    @Test
    @DisplayName("GET /subscriptions/active → 200 returns active subscription for tenant")
    void getActiveSubscription_found() throws Exception {
        String planId = createPlan("Active Sub Plan");
        createSubscription(planId);

        mockMvc.perform(get("/api/v1/subscriptions/active")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ── POST /api/v1/subscriptions/{id}/cancel ────────────────────────────────

    @Test
    @DisplayName("POST /subscriptions/{id}/cancel → 200 sets status CANCELLED")
    void cancelSubscription_returns200() throws Exception {
        String planId = createPlan("Cancel Sub Plan");
        String subId  = createSubscription(planId);

        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", subId)
                        .param("reason", "Cost reduction")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("Cost reduction"));
    }

    // ── POST /api/v1/subscriptions/{id}/suspend ───────────────────────────────

    @Test
    @DisplayName("POST /subscriptions/{id}/suspend → 200 sets status SUSPENDED")
    void suspendSubscription_returns200() throws Exception {
        String planId = createPlan("Suspend Sub Plan");
        String subId  = createSubscription(planId);

        mockMvc.perform(post("/api/v1/subscriptions/{id}/suspend", subId)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }
}
