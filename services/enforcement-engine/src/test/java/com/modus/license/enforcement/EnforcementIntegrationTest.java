package com.modus.license.enforcement;

import com.modus.license.enforcement.client.SessionGrpcClient;
import com.modus.license.enforcement.consumer.EntitlementEventConsumer;
import com.modus.license.enforcement.domain.cache.EntitlementCacheService;
import com.modus.license.enforcement.domain.event.EnforcementDecisionPublisher;
import com.modus.license.enforcement.domain.model.CachedEntitlement;
import com.modus.license.test.base.BaseRedisIntegrationTest;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Full-stack integration test for the Enforcement Engine.
 *
 * Starts a real Spring context against a Testcontainers Redis instance.
 * Kafka is handled via {@code mock://test} Schema Registry (see application-test.yml).
 * {@link EnforcementDecisionPublisher}, {@link EntitlementEventConsumer}, and
 * {@link SessionGrpcClient} are mocked to avoid needing real brokers or gRPC servers.
 */
@AutoConfigureWebTestClient
class EnforcementIntegrationTest extends BaseRedisIntegrationTest {

    /** Prevents auto-configuration from connecting to a real Keycloak issuer. */
    @MockBean JwtDecoder jwtDecoder;

    /** Avoids Kafka producer requirement; events verified separately in unit tests. */
    @MockBean EnforcementDecisionPublisher decisionPublisher;

    /** Avoids @KafkaListener registration and Kafka consumer connectivity. */
    @MockBean EntitlementEventConsumer entitlementEventConsumer;

    /** Avoids gRPC channel stub injection for the session service. */
    @MockBean SessionGrpcClient sessionClient;

    @Autowired WebTestClient webClient;
    @Autowired EntitlementCacheService cacheService;
    @Autowired ReactiveRedisConnectionFactory connectionFactory;

    static final String TENANT_ID      = TenantContextTestHelper.DEFAULT_TENANT_ID.value().toString();
    static final String USER_ID        = TenantContextTestHelper.DEFAULT_USER_ID.value().toString();
    static final String ENTITLEMENT_ID = UUID.randomUUID().toString();
    static final String FEATURE_KEY    = "feature.export";

    @BeforeEach
    void flushRedis() {
        connectionFactory.getReactiveConnection()
                .serverCommands()
                .flushAll()
                .block();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WebTestClient authed() {
        return webClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID)
                        .claim("tenant_id", TENANT_ID)
                        .claim("roles", List.of("TENANT_USER"))));
    }

    private void seedCache(String status) {
        CachedEntitlement entitlement = new CachedEntitlement(
                ENTITLEMENT_ID, TENANT_ID, "NAMED_USER", status,
                null, List.of(FEATURE_KEY, "feature.read"), "PROFESSIONAL", Instant.now());
        cacheService.put(entitlement).block();
    }

    private String checkBody(String featureKey) {
        return """
                {"userId":"%s","featureKey":"%s"}
                """.formatted(USER_ID, featureKey);
    }

    // ── POST /api/v1/enforcement/check ─────────────────────────────────────────

    @Test
    @DisplayName("POST /check → 200 ALLOWED when entitlement is ACTIVE and feature is granted")
    void checkAccess_allowed() {
        seedCache("ACTIVE");

        authed()
                .post().uri("/api/v1/enforcement/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(checkBody(FEATURE_KEY))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.allowed").isEqualTo(true)
                .jsonPath("$.data.decision").isEqualTo("ACCESS_ALLOWED")
                .jsonPath("$.data.entitlementId").isEqualTo(ENTITLEMENT_ID);
    }

    @Test
    @DisplayName("POST /check → 200 DENIED with ENTITLEMENT_NOT_FOUND when cache is empty")
    void checkAccess_denied_noEntitlement() {
        // No entitlement seeded — cache is empty after flushRedis()
        authed()
                .post().uri("/api/v1/enforcement/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(checkBody(FEATURE_KEY))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.allowed").isEqualTo(false)
                .jsonPath("$.data.decision").isEqualTo("ACCESS_DENIED")
                .jsonPath("$.data.denialReason").isEqualTo("ENTITLEMENT_NOT_FOUND");
    }

    @Test
    @DisplayName("POST /check → 200 DENIED with ENTITLEMENT_NOT_ACTIVE when status is EXPIRED")
    void checkAccess_denied_expired() {
        seedCache("EXPIRED");

        authed()
                .post().uri("/api/v1/enforcement/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(checkBody(FEATURE_KEY))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.allowed").isEqualTo(false)
                .jsonPath("$.data.denialReason").isEqualTo("ENTITLEMENT_NOT_ACTIVE");
    }

    @Test
    @DisplayName("POST /check → 200 DENIED with FEATURE_DISABLED when feature not in entitlement")
    void checkAccess_denied_featureDisabled() {
        seedCache("ACTIVE");

        authed()
                .post().uri("/api/v1/enforcement/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(checkBody("feature.admin"))   // not in the seeded entitlement
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.allowed").isEqualTo(false)
                .jsonPath("$.data.denialReason").isEqualTo("FEATURE_DISABLED");
    }

    @Test
    @DisplayName("POST /check → 400 when featureKey is blank")
    void checkAccess_missingFeatureKey() {
        authed()
                .post().uri("/api/v1/enforcement/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"%s"}
                        """.formatted(USER_ID))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /check → 401 when unauthenticated")
    void checkAccess_noAuth() {
        webClient
                .post().uri("/api/v1/enforcement/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(checkBody(FEATURE_KEY))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
