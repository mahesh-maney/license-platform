package com.modus.license.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.domain.enums.TenantStatus;
import com.modus.license.tenant.domain.entity.TenantEntity;
import com.modus.license.tenant.domain.event.TenantEventPublisher;
import com.modus.license.tenant.domain.repository.TenantRepository;
import com.modus.license.test.containers.PostgresTestContainer;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for Tenant Management.
 *
 * Uses a Testcontainers PostgreSQL instance with Flyway migrations.
 * {@link TenantEventPublisher} is mocked to avoid requiring a Kafka broker.
 * Each test runs inside a transaction that is rolled back after completion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class TenantIntegrationTest {

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

    /** Avoids needing a real Kafka broker; event publishing is verified in unit tests. */
    @MockBean TenantEventPublisher eventPublisher;

    @Autowired MockMvc         mockMvc;
    @Autowired ObjectMapper    objectMapper;
    @Autowired TenantRepository tenantRepository;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * POST processor that injects a mock JWT with ROLE_PLATFORM_ADMIN authority.
     *
     * The SecurityConfig sets JwtGrantedAuthoritiesConverter.setAuthorityPrefix(""),
     * so @PreAuthorize("hasAnyRole('PLATFORM_ADMIN')") requires the authority to be
     * stored as "ROLE_PLATFORM_ADMIN". We inject that directly via mockJwt().
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor tenantAdmin() {
        return jwt().authorities(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }

    private String body(String slug) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "slug",       slug,
                "name",       "Test Corp",
                "adminEmail", "admin@" + slug + ".com",
                "planTier",   "PROFESSIONAL",
                "region",     "eastus"
        ));
    }

    /** Creates a tenant via API and returns its ID from the JSON response. */
    private String createTenant(String slug) throws Exception {
        return mockMvc.perform(post("/api/v1/tenants")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(slug)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    // ── POST /api/v1/tenants ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /tenants → 201 Created with correct payload")
    void createTenant_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("acme-corp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("acme-corp"))
                .andExpect(jsonPath("$.data.status").value("TRIAL"))
                .andExpect(jsonPath("$.data.region").value("eastus"));
    }

    @Test
    @DisplayName("POST /tenants → 400 when required fields missing")
    void createTenant_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "only name"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /tenants → 409 when slug already exists")
    void createTenant_duplicateSlug_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup-slug")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tenants")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "slug",       "dup-slug",
                                "name",       "Another Corp",
                                "adminEmail", "other@dup-slug.com",
                                "planTier",   "FREE",
                                "region",     "westus"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /tenants → 401 when unauthenticated")
    void createTenant_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("no-auth-slug")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/tenants/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /tenants/{id} → 200 for existing tenant")
    void getTenant_found() throws Exception {
        String id = createTenant("get-test-slug");

        mockMvc.perform(get("/api/v1/tenants/{id}", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.slug").value("get-test-slug"));
    }

    @Test
    @DisplayName("GET /tenants/{id} → 404 for non-existent ID")
    void getTenant_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/{id}", UUID.randomUUID())
                        .with(platformAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/tenants/by-slug/{slug} ───────────────────────────────────

    @Test
    @DisplayName("GET /tenants/by-slug/{slug} → 200 for existing slug")
    void getTenantBySlug_found() throws Exception {
        createTenant("slug-lookup");

        mockMvc.perform(get("/api/v1/tenants/by-slug/{slug}", "slug-lookup")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("slug-lookup"));
    }

    // ── GET /api/v1/tenants ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /tenants → 200 returns paginated list")
    void listTenants_returns200() throws Exception {
        createTenant("list-tenant-a");
        createTenant("list-tenant-b");

        mockMvc.perform(get("/api/v1/tenants")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /tenants?status=TRIAL → 200 filters by status")
    void listTenantsByStatus_returns200() throws Exception {
        createTenant("trial-tenant");

        mockMvc.perform(get("/api/v1/tenants").param("status", "TRIAL")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── PUT /api/v1/tenants/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT /tenants/{id} → 200 updates name and planTier")
    void updateTenant_returns200() throws Exception {
        String id = createTenant("update-slug");

        mockMvc.perform(put("/api/v1/tenants/{id}", id)
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name",     "Updated Corp",
                                "planTier", "ENTERPRISE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Corp"))
                .andExpect(jsonPath("$.data.planTier").value("ENTERPRISE"));
    }

    // ── POST /api/v1/tenants/{id}/suspend ────────────────────────────────────

    @Test
    @DisplayName("POST /tenants/{id}/suspend → 200 sets status to SUSPENDED")
    void suspendTenant_returns200() throws Exception {
        String id = createTenant("suspend-slug");

        mockMvc.perform(post("/api/v1/tenants/{id}/suspend", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    // ── POST /api/v1/tenants/{id}/activate ───────────────────────────────────

    @Test
    @DisplayName("POST /tenants/{id}/activate → 200 sets status to ACTIVE")
    void activateTenant_returns200() throws Exception {
        String id = createTenant("activate-slug");

        // First suspend it so we have something to activate
        mockMvc.perform(post("/api/v1/tenants/{id}/suspend", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tenants/{id}/activate", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ── DELETE /api/v1/tenants/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("DELETE /tenants/{id} → 204 soft-deletes tenant")
    void deleteTenant_returns204() throws Exception {
        String id = createTenant("delete-slug");

        mockMvc.perform(delete("/api/v1/tenants/{id}", id)
                        .with(platformAdmin()))
                .andExpect(status().isNoContent());

        // Verify soft-delete: entity still exists with INACTIVE status
        TenantEntity entity = tenantRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(TenantStatus.INACTIVE);
    }
}
