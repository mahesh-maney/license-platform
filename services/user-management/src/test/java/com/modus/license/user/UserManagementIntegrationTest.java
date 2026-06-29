package com.modus.license.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.test.containers.PostgresTestContainer;
import com.modus.license.test.context.TenantContextTestHelper;
import com.modus.license.user.domain.event.UserEventPublisher;
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
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the User Management service.
 *
 * Covers /api/v1/users (UserController) end-to-end against Testcontainers PostgreSQL.
 *
 * {@link UserEventPublisher} is mocked to avoid needing a real Kafka broker.
 * Each test runs inside a transaction that rolls back automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class UserManagementIntegrationTest {

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
    @MockBean UserEventPublisher eventPublisher;

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    static final String TENANT_ID =
            TenantContextTestHelper.DEFAULT_TENANT_ID.value().toString();
    static final String USER_ID   =
            TenantContextTestHelper.DEFAULT_USER_ID.value().toString();

    // ── Security helpers ──────────────────────────────────────────────────────

    private static org.springframework.test.web.servlet.request.RequestPostProcessor tenantAdmin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                             new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
                .jwt(j -> j.subject(USER_ID)
                           .claim("tenant_id", TENANT_ID)
                           .claim("roles", List.of("PLATFORM_ADMIN", "TENANT_ADMIN")));
    }

    // ── Request body helpers ───────────────────────────────────────────────────

    private String userBody(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email",     email,
                "firstName", "Alice",
                "lastName",  "Smith",
                "roles",     Set.of("TENANT_USER")
        ));
    }

    /** Creates a user and returns its id. */
    private UUID createUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String idStr = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        return UUID.fromString(idStr);
    }

    // ── POST /api/v1/users ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /users → 201 Created with email and ACTIVE status")
    void createUser_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("new.user@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /users → 409 when email already exists for tenant")
    void createUser_duplicate_returns409() throws Exception {
        createUser("dup@example.com");

        mockMvc.perform(post("/api/v1/users")
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("dup@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /users → 400 when email is missing")
    void createUser_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Alice"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /users → 401 when unauthenticated")
    void createUser_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("anon@example.com")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/users/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/{id} → 200 for existing user")
    void getUser_found() throws Exception {
        UUID id = createUser("get.by.id@example.com");

        mockMvc.perform(get("/api/v1/users/{id}", id)
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.email").value("get.by.id@example.com"));
    }

    @Test
    @DisplayName("GET /users/{id} → 404 for non-existent id")
    void getUser_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID())
                        .with(tenantAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/users/by-email/{email} ───────────────────────────────────

    @Test
    @DisplayName("GET /users/by-email/{email} → 200 for existing email")
    void getUserByEmail_found() throws Exception {
        createUser("by.email@example.com");

        mockMvc.perform(get("/api/v1/users/by-email/{email}", "by.email@example.com")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("by.email@example.com"));
    }

    @Test
    @DisplayName("GET /users/by-email/{email} → 404 for unknown email")
    void getUserByEmail_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/by-email/{email}", "nobody@example.com")
                        .with(tenantAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/users ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users → 200 returns paginated list")
    void listUsers_returns200() throws Exception {
        createUser("list.a@example.com");
        createUser("list.b@example.com");

        mockMvc.perform(get("/api/v1/users")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /users?status=ACTIVE → 200 filters by status")
    void listUsers_filterByStatus() throws Exception {
        createUser("status.filter@example.com");

        mockMvc.perform(get("/api/v1/users").param("status", "ACTIVE")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── PUT /api/v1/users/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /users/{id} → 200 updates firstName and lastName")
    void updateUser_returns200() throws Exception {
        UUID id = createUser("update@example.com");

        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Updated",
                                "lastName",  "Name"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Updated"))
                .andExpect(jsonPath("$.data.lastName").value("Name"));
    }

    // ── PUT /api/v1/users/{id}/roles ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /users/{id}/roles → 200 replaces role set")
    void assignRoles_returns200() throws Exception {
        UUID id = createUser("roles@example.com");

        mockMvc.perform(put("/api/v1/users/{id}/roles", id)
                        .with(tenantAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roles", Set.of("TENANT_ADMIN")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray());
    }

    // ── POST /api/v1/users/{id}/deactivate ───────────────────────────────────

    @Test
    @DisplayName("POST /users/{id}/deactivate → 200 sets status INACTIVE")
    void deactivateUser_returns200() throws Exception {
        UUID id = createUser("deactivate@example.com");

        mockMvc.perform(post("/api/v1/users/{id}/deactivate", id)
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    // ── POST /api/v1/users/{id}/reactivate ───────────────────────────────────

    @Test
    @DisplayName("POST /users/{id}/reactivate → 200 sets status ACTIVE")
    void reactivateUser_returns200() throws Exception {
        UUID id = createUser("reactivate@example.com");

        // Deactivate first so reactivate has something to change
        mockMvc.perform(post("/api/v1/users/{id}/deactivate", id)
                        .with(tenantAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/{id}/reactivate", id)
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ── DELETE /api/v1/users/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /users/{id} → 204 soft-deletes user")
    void deleteUser_returns204() throws Exception {
        UUID id = createUser("delete@example.com");

        mockMvc.perform(delete("/api/v1/users/{id}", id)
                        .with(tenantAdmin()))
                .andExpect(status().isNoContent());
    }
}
