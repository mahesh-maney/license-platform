package com.modus.license.gateway.controller;

import com.modus.license.core.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Handles circuit-breaker fallback responses for all routed services.
 *
 * <p>When Resilience4j opens the circuit for a downstream service, Spring Cloud
 * Gateway forwards the request to {@code /fallback/{service}}. This controller
 * returns a structured {@link ErrorResponse} with HTTP 503.
 *
 * <p>Fallback URIs are configured in {@code application.yml} under each route's
 * {@code CircuitBreaker} filter:
 * <pre>
 *   fallbackUri: forward:/fallback/tenant-management
 * </pre>
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping("/fallback/{service}")
    public Mono<ResponseEntity<ErrorResponse>> fallback(
            @PathVariable String service,
            ServerWebExchange exchange) {

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst("X-Correlation-Id");

        log.warn("Circuit breaker open for service={} correlationId={}", service, correlationId);

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.internal(
                        "Service '" + service + "' is temporarily unavailable. "
                        + "Please try again in a few moments.")));
    }
}
