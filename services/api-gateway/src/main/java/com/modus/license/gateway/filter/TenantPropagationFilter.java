package com.modus.license.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global gateway filter that propagates tenant context as HTTP headers
 * to all downstream services.
 *
 * <p>Headers added:
 * <ul>
 *   <li>{@code X-Tenant-Id} — value of the {@code tenant_id} JWT claim</li>
 *   <li>{@code X-User-Id}   — value of the JWT subject ({@code sub} claim)</li>
 *   <li>{@code X-Correlation-Id} — echoed from the request, or a new UUID if absent</li>
 * </ul>
 *
 * <p>Downstream services can read these headers for routing and logging without
 * re-parsing the JWT. Spring Security on each service also validates the JWT
 * independently for defence-in-depth.
 *
 * <p>Order -1: runs after Spring Security authentication, before routing filters.
 */
@Component
public class TenantPropagationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantPropagationFilter.class);

    static final String HEADER_TENANT_ID      = "X-Tenant-Id";
    static final String HEADER_USER_ID        = "X-User-Id";
    static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String inboundCorrelationId = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID);
        String correlationId = (inboundCorrelationId != null && !inboundCorrelationId.isBlank())
                ? inboundCorrelationId
                : UUID.randomUUID().toString();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    String tenantId = jwtAuth.getToken().getClaimAsString("tenant_id");
                    String userId   = jwtAuth.getToken().getSubject();

                    log.debug("Propagating context: tenantId={} userId={} correlationId={}",
                            tenantId, userId, correlationId);

                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header(HEADER_TENANT_ID,      tenantId      != null ? tenantId      : "")
                            .header(HEADER_USER_ID,        userId        != null ? userId        : "")
                            .header(HEADER_CORRELATION_ID, correlationId)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                // Unauthenticated requests (permitted paths) still get a correlation ID
                .switchIfEmpty(Mono.defer(() -> {
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header(HEADER_CORRELATION_ID, correlationId)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                }));
    }
}
