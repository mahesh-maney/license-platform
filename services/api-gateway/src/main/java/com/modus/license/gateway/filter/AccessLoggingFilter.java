package com.modus.license.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global gateway filter that logs every proxied request with method, path,
 * response status, and latency.
 *
 * <p>Runs at {@link Ordered#LOWEST_PRECEDENCE} so it wraps the full filter chain
 * and captures the final response status after all other filters have run.
 */
@Component
public class AccessLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLoggingFilter.class);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startMs = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders()
                .getFirst(TenantPropagationFilter.HEADER_CORRELATION_ID);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("method={} path={} status={} duration={}ms correlationId={}",
                    request.getMethod(),
                    request.getURI().getPath(),
                    exchange.getResponse().getStatusCode(),
                    durationMs,
                    correlationId);
        }));
    }
}
