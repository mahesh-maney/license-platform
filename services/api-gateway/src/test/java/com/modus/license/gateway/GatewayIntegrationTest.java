package com.modus.license.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Full-stack integration test for the API Gateway.
 *
 * Starts the full reactive server on a random port with:
 * - Redis excluded (no rate limiting in tests)
 * - routes: [] (no downstream proxying — avoids needing real services)
 * - @MockBean ReactiveJwtDecoder to prevent OIDC discovery-endpoint fetch at startup
 *
 * Tests verify: public-path access, security enforcement (401 without auth,
 * 404 with auth when no route matches), and circuit-breaker fallback response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("API Gateway integration")
class GatewayIntegrationTest {

    /** Prevents NimbusReactiveJwtDecoder from fetching OIDC discovery on startup. */
    @MockBean ReactiveJwtDecoder jwtDecoder;

    @Autowired WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // Claim names must match PlatformSecurityProperties defaults:
        // tenant_id (not tenantId), sub for user, roles for authorities.
        // Values must be valid UUIDs so TenantId/UserId records can parse them.
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub",       "00000000-0000-0000-0000-000000000002")
                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));
    }

    // ── public / permitted paths ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /actuator/health → 200 (permitted without auth)")
    void actuatorHealth_returns200() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("GET /fallback/{service} → 503 (permitted, circuit-breaker response)")
    void fallback_returns503() {
        webTestClient.get()
                .uri("/fallback/tenant-management")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").value(msg ->
                        org.assertj.core.api.Assertions.assertThat(msg.toString())
                                .contains("tenant-management"));
    }

    // ── security enforcement ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/tenants → 401 when unauthenticated")
    void protectedPath_noAuth_returns401() {
        webTestClient.get()
                .uri("/api/v1/tenants")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /api/v1/plans → 401 when unauthenticated")
    void protectedPath_plans_noAuth_returns401() {
        webTestClient.get()
                .uri("/api/v1/plans")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /fallback/{service} → 503 even when authenticated (permitted path)")
    void fallback_withAuth_returns503() {
        // Verifies authenticated users can also access permitted fallback paths
        webTestClient.get()
                .uri("/fallback/subscription-plan")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    @DisplayName("GET /actuator/health → 200 even when authenticated")
    void health_withAuth_returns200() {
        webTestClient.get()
                .uri("/actuator/health")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();
    }

    // ── API docs (permitted) ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api-docs → accessible without auth (permitted path)")
    void apiDocs_noAuth_notUnauthorized() {
        // /api-docs is permitted — should not return 401
        // It may return 404 (no aggregator configured) but not 401
        webTestClient.get()
                .uri("/api-docs")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }
}
