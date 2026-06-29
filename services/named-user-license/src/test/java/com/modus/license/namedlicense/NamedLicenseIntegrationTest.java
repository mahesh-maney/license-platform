package com.modus.license.namedlicense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.namedlicense.consumer.EntitlementEventConsumer;
import com.modus.license.namedlicense.domain.event.NamedLicenseEventPublisher;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Named-User License service.
 *
 * Covers /api/v1/named-licenses (NamedLicenseController) end-to-end
 * against Testcontainers PostgreSQL.
 *
 * {@link NamedLicenseEventPublisher} and {@link EntitlementEventConsumer} are mocked
 * to avoid needing a real Kafka broker.
 * Each test runs inside a transaction that rolls back automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class NamedLicenseIntegrationTest {

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

    @MockBean NamedLicenseEventPublisher eventPublisher;
    @MockBean EntitlementEventConsumer   entitlementEventConsumer;

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    static final String TENANT_ID =
            TenantContextTestHelper.DEFAULT_TENANT_ID.value().toString();
    static final String USER_ID   =
            TenantContextTestHelper.DEFAULT_USER_ID.value().toString();

    // ── Security helpers ──────────────────────────────────────────────────────

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                             new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
                .jwt(j -> j.subject(USER_ID)
                           .claim("tenant_id", TENANT_ID)
                           .claim("roles", List.of("PLATFORM_ADMIN", "TENANT_ADMIN")));
    }

    // ── Request body helpers ───────────────────────────────────────────────────

    private String poolBody(UUID planId, UUID entitlementId, int totalSeats) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "planId",        planId,
                "entitlementId", entitlementId,
                "totalSeats",    totalSeats
        ));
    }

    /** Creates a pool and returns its generated UUID. */
    private UUID createPool(UUID planId, UUID entitlementId, int totalSeats) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/named-licenses")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(poolBody(planId, entitlementId, totalSeats)))
                .andExpect(status().isCreated())
                .andReturn();
        String idStr = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        return UUID.fromString(idStr);
    }

    // ── POST /api/v1/named-licenses ───────────────────────────────────────────

    @Test
    @DisplayName("POST /named-licenses → 201 Created with totalSeats and zero usedSeats")
    void createPool_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/named-licenses")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(poolBody(UUID.randomUUID(), UUID.randomUUID(), 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalSeats").value(10))
                .andExpect(jsonPath("$.data.usedSeats").value(0));
    }

    @Test
    @DisplayName("POST /named-licenses → 409 when pool already exists for entitlement")
    void createPool_duplicate_returns409() throws Exception {
        UUID planId        = UUID.randomUUID();
        UUID entitlementId = UUID.randomUUID();
        createPool(planId, entitlementId, 10);

        mockMvc.perform(post("/api/v1/named-licenses")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(poolBody(planId, entitlementId, 5)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /named-licenses → 400 when required fields missing")
    void createPool_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/named-licenses")
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("totalSeats", 5))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /named-licenses → 401 when unauthenticated")
    void createPool_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/named-licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(poolBody(UUID.randomUUID(), UUID.randomUUID(), 10)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/named-licenses/{id} ──────────────────────────────────────

    @Test
    @DisplayName("GET /named-licenses/{id} → 200 for existing pool")
    void getPool_found() throws Exception {
        UUID id = createPool(UUID.randomUUID(), UUID.randomUUID(), 5);

        mockMvc.perform(get("/api/v1/named-licenses/{id}", id)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    @DisplayName("GET /named-licenses/{id} → 404 for non-existent pool")
    void getPool_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/named-licenses/{id}", UUID.randomUUID())
                        .with(platformAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/named-licenses ────────────────────────────────────────────

    @Test
    @DisplayName("GET /named-licenses → 200 returns paginated list")
    void listPools_returns200() throws Exception {
        createPool(UUID.randomUUID(), UUID.randomUUID(), 10);
        createPool(UUID.randomUUID(), UUID.randomUUID(), 20);

        mockMvc.perform(get("/api/v1/named-licenses")
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── POST /api/v1/named-licenses/{id}/seats ────────────────────────────────

    @Test
    @DisplayName("POST /{id}/seats → 201 assigns seat and returns assignment")
    void assignSeat_returns201() throws Exception {
        UUID licenseId = createPool(UUID.randomUUID(), UUID.randomUUID(), 5);
        UUID userId    = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", licenseId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "email",  "user@example.com"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("POST /{id}/seats → 409 when user already holds a seat")
    void assignSeat_duplicate_returns409() throws Exception {
        UUID licenseId = createPool(UUID.randomUUID(), UUID.randomUUID(), 5);
        UUID userId    = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", licenseId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "email",  "user@example.com"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", licenseId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "email",  "user@example.com"
                        ))))
                .andExpect(status().isConflict());
    }

    // ── DELETE /api/v1/named-licenses/{id}/seats/{userId} ────────────────────

    @Test
    @DisplayName("DELETE /{id}/seats/{userId} → 204 revokes seat")
    void revokeSeat_returns204() throws Exception {
        UUID licenseId = createPool(UUID.randomUUID(), UUID.randomUUID(), 5);
        UUID userId    = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", licenseId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "email",  "user@example.com"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/named-licenses/{id}/seats/{userId}", licenseId, userId)
                        .with(platformAdmin()))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/v1/named-licenses/{id}/seats/transfer ───────────────────────

    @Test
    @DisplayName("POST /{id}/seats/transfer → 200 transfers seat from one user to another")
    void transferSeat_returns200() throws Exception {
        UUID licenseId = createPool(UUID.randomUUID(), UUID.randomUUID(), 5);
        UUID userA     = UUID.randomUUID();
        UUID userB     = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", licenseId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userA,
                                "email",  "user_a@example.com"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats/transfer", licenseId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fromUserId",  userA,
                                "toUserId",    userB,
                                "toUserEmail", "user_b@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userB.toString()));
    }

    // ── GET /api/v1/named-licenses/{id}/seats ────────────────────────────────

    @Test
    @DisplayName("GET /{id}/seats → 200 returns paginated seat assignments")
    void listAssignments_returns200() throws Exception {
        UUID licenseId = createPool(UUID.randomUUID(), UUID.randomUUID(), 5);
        UUID userId    = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", licenseId)
                        .with(platformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "email",  "user@example.com"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/named-licenses/{id}/seats", licenseId)
                        .with(platformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
