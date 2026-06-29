package com.modus.license.notification;

import com.modus.license.notification.consumer.SubscriptionEventConsumer;
import com.modus.license.notification.consumer.TenantEventConsumer;
import com.modus.license.notification.domain.event.NotificationEventPublisher;
import com.modus.license.notification.service.EmailDispatchService;
import com.modus.license.notification.service.WebhookDispatchService;
import com.modus.license.test.containers.PostgresTestContainer;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the Notification service.
 *
 * Covers /api/v1/notifications (NotificationController) end-to-end against
 * Testcontainers PostgreSQL.
 *
 * Both Kafka consumers and dispatch services are mocked so no broker or SMTP
 * server is needed in the test environment.
 *
 * Each test runs inside a transaction that rolls back automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class NotificationIntegrationTest {

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
    @MockBean TenantEventConsumer       tenantEventConsumer;
    @MockBean SubscriptionEventConsumer subscriptionEventConsumer;

    /** Mock dispatch services to prevent SMTP / HTTP calls. */
    @MockBean EmailDispatchService   emailDispatchService;
    @MockBean WebhookDispatchService webhookDispatchService;

    /** Mock publisher to prevent Kafka publishing. */
    @MockBean NotificationEventPublisher notificationEventPublisher;

    @Autowired MockMvc mockMvc;

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

    // ── GET /api/v1/notifications ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /notifications → 200 with empty content (no data seeded)")
    void getNotifications_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /notifications → 401 when unauthenticated")
    void getNotifications_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/notifications/{notificationId} ────────────────────────────

    @Test
    @DisplayName("GET /notifications/{id} → 404 when entry does not exist")
    void getNotificationById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/" + UUID.randomUUID())
                        .with(tenantAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /notifications/{id} → 401 when unauthenticated")
    void getNotificationById_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
