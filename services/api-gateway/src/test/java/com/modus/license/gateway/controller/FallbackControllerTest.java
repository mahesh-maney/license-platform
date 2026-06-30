package com.modus.license.gateway.controller;

import com.modus.license.core.web.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FallbackController")
class FallbackControllerTest {

    final FallbackController controller = new FallbackController();

    @Test
    @DisplayName("fallback → 503 SERVICE_UNAVAILABLE with service name in message")
    void fallback_returnsServiceUnavailableWithServiceName() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback/tenant-management").build());

        StepVerifier.create(controller.fallback("tenant-management", exchange))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().message()).contains("tenant-management");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fallback → correlation ID from header is read without error")
    void fallback_withCorrelationId_completesNormally() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback/entitlement")
                        .header("X-Correlation-Id", "trace-id-123")
                        .build());

        StepVerifier.create(controller.fallback("entitlement", exchange))
                .assertNext(response ->
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    @DisplayName("fallback → different services produce distinct messages")
    void fallback_differentServices_distinctMessages() {
        MockServerWebExchange ex1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback/session").build());
        MockServerWebExchange ex2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback/audit").build());

        ResponseEntity<ErrorResponse> r1 = controller.fallback("session", ex1).block();
        ResponseEntity<ErrorResponse> r2 = controller.fallback("audit",   ex2).block();

        assertThat(r1.getBody().message()).contains("session");
        assertThat(r2.getBody().message()).contains("audit");
        assertThat(r1.getBody().message()).isNotEqualTo(r2.getBody().message());
    }
}
