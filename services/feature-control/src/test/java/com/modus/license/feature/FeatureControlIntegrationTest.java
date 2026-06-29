package com.modus.license.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.feature.domain.event.FeatureEventPublisher;
import com.modus.license.feature.domain.repository.FeatureDefinitionRepository;
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

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Feature Control service.
 *
 * Covers /api/v1/features (FeatureController) and /api/v1/tenant-features
 * (TenantFeatureController) end-to-end against Testcontainers PostgreSQL.
 *
 * {@link FeatureEventPublisher} is mocked to avoid needing a Kafka broker.
 * Each test runs inside a transaction that rolls back automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class FeatureControlIntegrationTest {

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

    /** Avoids needing a real Kafka broker. */
    @MockBean FeatureEventPublisher eventPublisher;

    @Autowired MockMvc                    mockMvc;
    @Autowired ObjectMapper               objectMapper;
    @Autowired FeatureDefinitionRepository featureRepository;

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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor tenantAdmin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                             new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
                .jwt(j -> j.subject(USER_ID)
                           .claim("tenant_id", TENANT_ID)
                           .claim("roles", java.util.List.of("PLATFORM_ADMIN", "TENANT_ADMIN")));
    }

    // ── Request body helpers ──────────────────────────────────────────────────

    private String featureBody(String key) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "featureKey",      key,
                "name",            "Test Feature",
                "description",     "A test feature",
                "minimumPlanTier", "PROFESSIONAL",
                "status",          "ENABLED"
        ));
    }

    /** Creates a feature via API and returns its featureKey. */
    private String createFeature(String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/features")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(featureBody(key)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("featureKey").asText();
    }

    // ── POST /api/v1/features ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /features → 201 Created with correct featureKey and status")
    void createFeature_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/features")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(featureBody("DARK_MODE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.featureKey").value("DARK_MODE"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    @Test
    @DisplayName("POST /features → 409 when featureKey already exists")
    void createFeature_duplicateKey_returns409() throws Exception {
        createFeature("DUPE_KEY");

        mockMvc.perform(post("/api/v1/features")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(featureBody("DUPE_KEY")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /features → 400 when required fields missing")
    void createFeature_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/features")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Incomplete"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /features → 401 when unauthenticated")
    void createFeature_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(featureBody("UNAUTH_FEAT")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/features/{featureKey} ─────────────────────────────────────

    @Test
    @DisplayName("GET /features/{key} → 200 for existing feature")
    void getFeature_found() throws Exception {
        createFeature("GET_FEATURE");

        mockMvc.perform(get("/api/v1/features/{key}", "GET_FEATURE")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featureKey").value("GET_FEATURE"));
    }

    @Test
    @DisplayName("GET /features/{key} → 404 for non-existent key")
    void getFeature_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/features/{key}", "DOES_NOT_EXIST")
                        .with(platformAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/features ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /features → 200 returns paginated list")
    void listFeatures_returns200() throws Exception {
        createFeature("LIST_FEAT_A");
        createFeature("LIST_FEAT_B");

        mockMvc.perform(get("/api/v1/features")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /features?status=ENABLED → 200 filters by status")
    void listFeatures_filterByStatus() throws Exception {
        createFeature("ENABLED_FEAT");

        mockMvc.perform(get("/api/v1/features").param("status", "ENABLED")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── PATCH /api/v1/features/{featureKey} ───────────────────────────────────

    @Test
    @DisplayName("PATCH /features/{key} → 200 updates name and description")
    void updateFeature_returns200() throws Exception {
        createFeature("UPDATE_FEAT");

        mockMvc.perform(patch("/api/v1/features/{key}", "UPDATE_FEAT")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name",        "Updated Name",
                                "description", "Updated description"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.description").value("Updated description"));
    }

    // ── POST /api/v1/features/{key}/enable ───────────────────────────────────

    @Test
    @DisplayName("POST /features/{key}/enable → 200 sets status ENABLED")
    void enableFeature_returns200() throws Exception {
        // Create as DISABLED first so enable has something to change
        String body = objectMapper.writeValueAsString(Map.of(
                "featureKey", "ENABLE_TEST",
                "name",       "Enable Test",
                "status",     "DISABLED"
        ));
        mockMvc.perform(post("/api/v1/features")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/features/{key}/enable", "ENABLE_TEST")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    // ── POST /api/v1/features/{key}/disable ──────────────────────────────────

    @Test
    @DisplayName("POST /features/{key}/disable → 200 sets status DISABLED")
    void disableFeature_returns200() throws Exception {
        createFeature("DISABLE_TEST");

        mockMvc.perform(post("/api/v1/features/{key}/disable", "DISABLE_TEST")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    // ── POST /api/v1/features/{key}/deprecate ────────────────────────────────

    @Test
    @DisplayName("POST /features/{key}/deprecate → 200 sets status DEPRECATED")
    void deprecateFeature_returns200() throws Exception {
        createFeature("DEPRECATE_TEST");

        mockMvc.perform(post("/api/v1/features/{key}/deprecate", "DEPRECATE_TEST")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEPRECATED"));
    }

    // ── GET /api/v1/tenant-features ──────────────────────────────────────────

    @Test
    @DisplayName("GET /tenant-features → 200 returns effective features for tenant")
    void listTenantFeatures_returns200() throws Exception {
        createFeature("TENANT_LIST_FEAT");

        mockMvc.perform(get("/api/v1/tenant-features")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── GET /api/v1/tenant-features/{featureKey} ──────────────────────────────

    @Test
    @DisplayName("GET /tenant-features/{key} → 200 returns effective feature for tenant")
    void getTenantFeature_returns200() throws Exception {
        createFeature("TENANT_GET_FEAT");

        mockMvc.perform(get("/api/v1/tenant-features/{key}", "TENANT_GET_FEAT")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featureKey").value("TENANT_GET_FEAT"))
                .andExpect(jsonPath("$.data.effectiveStatus").value("ENABLED"));
    }

    // ── PUT /api/v1/tenant-features/{featureKey} ──────────────────────────────

    @Test
    @DisplayName("PUT /tenant-features/{key} → 200 sets tenant override")
    void setTenantOverride_returns200() throws Exception {
        createFeature("OVERRIDE_FEAT");

        mockMvc.perform(put("/api/v1/tenant-features/{key}", "OVERRIDE_FEAT")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overriddenStatus").value("DISABLED"))
                .andExpect(jsonPath("$.data.effectiveStatus").value("DISABLED"));
    }

    // ── DELETE /api/v1/tenant-features/{featureKey} ───────────────────────────

    @Test
    @DisplayName("DELETE /tenant-features/{key} → 204 removes override")
    void removeTenantOverride_returns204() throws Exception {
        createFeature("REMOVE_OVERRIDE_FEAT");

        // First set an override
        mockMvc.perform(put("/api/v1/tenant-features/{key}", "REMOVE_OVERRIDE_FEAT")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk());

        // Then remove it
        mockMvc.perform(delete("/api/v1/tenant-features/{key}", "REMOVE_OVERRIDE_FEAT")
                        .with(platformAdmin()))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/v1/tenant-features/admin/{tenantId} ─────────────────────────

    @Test
    @DisplayName("GET /tenant-features/admin/{tenantId} → 200 returns features for specified tenant")
    void listFeaturesForTenant_returns200() throws Exception {
        createFeature("ADMIN_LIST_FEAT");

        mockMvc.perform(get("/api/v1/tenant-features/admin/{tenantId}", TENANT_ID)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
