package com.modus.license.observability.filter;

import com.modus.license.core.context.TenantContext;
import com.modus.license.observability.mdc.MdcKeys;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux filter for reactive (WebFlux/R2DBC) services.
 *
 * Reads the {@link TenantContext} from Reactor Context (written by
 * {@code TenantContextWebFilter} in platform-security) and:
 *  1. Sets MDC for the subscriber thread (covers synchronous log statements).
 *  2. The {@link com.modus.license.observability.mdc.TenantMdcAccessor} registered
 *     in {@link com.modus.license.observability.config.TracingConfiguration}
 *     then propagates tenantId/userId across thread switches automatically.
 *
 * Order: 2 — runs after security (0) and TenantContextWebFilter (1).
 */
public class ReactiveMdcWebFilter implements WebFilter, Ordered {

    private final String serviceName;
    private final String region;

    public ReactiveMdcWebFilter(String serviceName, String region) {
        this.serviceName = serviceName;
        this.region      = region;
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        MDC.put(MdcKeys.SERVICE_NAME, serviceName);
        MDC.put(MdcKeys.REGION,       region);

        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        }

        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(TenantContext.REACTOR_CONTEXT_KEY)) {
                TenantContext tenantCtx = ctx.get(TenantContext.REACTOR_CONTEXT_KEY);
                MDC.put(MdcKeys.TENANT_ID, tenantCtx.tenantId().toString());
                MDC.put(MdcKeys.USER_ID,   tenantCtx.userId().toString());
            }
            return chain.filter(exchange);
        }).doFinally(signal -> {
            MDC.remove(MdcKeys.TENANT_ID);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.CORRELATION_ID);
            MDC.remove(MdcKeys.SERVICE_NAME);
            MDC.remove(MdcKeys.REGION);
        });
    }
}
