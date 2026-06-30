package com.modus.license.security.filter;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.exception.TenantContextException;
import com.modus.license.security.jwt.JwtClaimsExtractor;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Reactive WebFilter for WebFlux services (enforcement-engine, session, etc.).
 *
 * Runs after Spring Security has authenticated the JWT. Extracts the tenant
 * context from the validated token and writes it into the Reactor Context so
 * that downstream operators can retrieve it via {@link ReactorTenantContextUtil}.
 *
 * Order 1 — must run after Spring Security's authentication filter.
 */
public class TenantContextWebFilter implements WebFilter, Ordered {

    private final JwtClaimsExtractor extractor;

    public TenantContextWebFilter(JwtClaimsExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Use thenReturn(true) so flatMap emits a value in both the success and error paths.
        // Without it, Mono<Void> (from setComplete or chain.filter) completes without emitting
        // an element, which incorrectly triggers switchIfEmpty and invokes chain.filter a second
        // time after the 401 response is already committed.
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    try {
                        TenantContext ctx = extractor.extract(jwtAuth.getToken());
                        return chain.filter(exchange)
                                .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, ctx))
                                .thenReturn(true);
                    } catch (TenantContextException e) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete().thenReturn(true);
                    }
                })
                .switchIfEmpty(chain.filter(exchange).thenReturn(true))
                .then();
    }
}
