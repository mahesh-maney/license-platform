package com.modus.license.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("TenantPropagationFilter")
class TenantPropagationFilterTest {

    final TenantPropagationFilter filter = new TenantPropagationFilter();

    static final String TENANT_ID = UUID.randomUUID().toString();
    static final String USER_ID   = UUID.randomUUID().toString();

    private Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("tenant_id", TENANT_ID)
                .subject(USER_ID)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private ServerHttpRequest run(MockServerWebExchange exchange, boolean withAuth) {
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex.getRequest());
            return Mono.empty();
        };

        Mono<Void> mono = filter.filter(exchange, chain);
        if (withAuth) {
            JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt());
            mono = mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        }
        mono.block();
        return captured.get();
    }

    // ── order ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder() returns -1 (runs after security, before routing)")
    void order_isMinusOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    // ── authenticated ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("authenticated → X-Tenant-Id and X-User-Id propagated from JWT claims")
    void authenticated_propagatesTenantAndUser() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        ServerHttpRequest result = run(exchange, true);

        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_TENANT_ID))
                .isEqualTo(TENANT_ID);
        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_USER_ID))
                .isEqualTo(USER_ID);
        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_CORRELATION_ID))
                .isNotNull();
    }

    @Test
    @DisplayName("authenticated with inbound X-Correlation-Id → echoed, not replaced")
    void authenticated_inboundCorrelationId_isEchoed() {
        String existingId = "my-trace-id-abc";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test")
                        .header(TenantPropagationFilter.HEADER_CORRELATION_ID, existingId)
                        .build());

        ServerHttpRequest result = run(exchange, true);

        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_CORRELATION_ID))
                .isEqualTo(existingId);
    }

    @Test
    @DisplayName("authenticated without X-Correlation-Id → new UUID generated")
    void authenticated_missingCorrelationId_generatesUuid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        ServerHttpRequest result = run(exchange, true);

        String correlationId = result.getHeaders().getFirst(TenantPropagationFilter.HEADER_CORRELATION_ID);
        assertThat(correlationId).isNotNull();
        assertThatCode(() -> UUID.fromString(correlationId)).doesNotThrowAnyException();
    }

    // ── unauthenticated ───────────────────────────────────────────────────────

    @Test
    @DisplayName("unauthenticated → only X-Correlation-Id set (no tenant/user headers)")
    void unauthenticated_setsOnlyCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());

        ServerHttpRequest result = run(exchange, false);

        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_CORRELATION_ID))
                .isNotNull();
        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_TENANT_ID))
                .isNull();
        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_USER_ID))
                .isNull();
    }

    @Test
    @DisplayName("unauthenticated with inbound X-Correlation-Id → echoed")
    void unauthenticated_inboundCorrelationId_isEchoed() {
        String existingId = "unauthenticated-trace";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(TenantPropagationFilter.HEADER_CORRELATION_ID, existingId)
                        .build());

        ServerHttpRequest result = run(exchange, false);

        assertThat(result.getHeaders().getFirst(TenantPropagationFilter.HEADER_CORRELATION_ID))
                .isEqualTo(existingId);
    }
}
