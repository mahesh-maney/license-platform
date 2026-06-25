package com.modus.license.security.filter;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.exception.TenantContextException;
import reactor.core.publisher.Mono;

/**
 * Utility for reactive services to retrieve the {@link TenantContext}
 * from the Reactor Context written by {@link TenantContextWebFilter}.
 *
 * Usage in a service method:
 * <pre>
 *   return ReactorTenantContextUtil.getTenantContext()
 *       .flatMap(ctx -> repository.findByTenantId(ctx.tenantId().value()));
 * </pre>
 */
public final class ReactorTenantContextUtil {

    private ReactorTenantContextUtil() {}

    /**
     * Returns the {@link TenantContext} stored in the current Reactor Context.
     * Emits {@link TenantContextException} if not present.
     */
    public static Mono<TenantContext> getTenantContext() {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(TenantContext.REACTOR_CONTEXT_KEY)) {
                return Mono.just(ctx.<TenantContext>get(TenantContext.REACTOR_CONTEXT_KEY));
            }
            return Mono.error(TenantContextException::missing);
        });
    }

    /**
     * Returns the {@link TenantContext} or empty if not present.
     * Use when the endpoint is optionally authenticated.
     */
    public static Mono<TenantContext> getTenantContextOrEmpty() {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(TenantContext.REACTOR_CONTEXT_KEY)) {
                return Mono.just(ctx.<TenantContext>get(TenantContext.REACTOR_CONTEXT_KEY));
            }
            return Mono.empty();
        });
    }
}
