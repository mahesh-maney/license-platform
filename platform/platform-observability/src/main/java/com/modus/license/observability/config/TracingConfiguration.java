package com.modus.license.observability.config;

import com.modus.license.observability.mdc.TenantMdcAccessor;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * Configures distributed tracing (Micrometer Tracing + Brave + Zipkin).
 *
 * Key responsibilities:
 *  1. Enables Reactor's automatic context propagation so that Micrometer trace
 *     context (traceId, spanId) and our TenantContext flow across thread-pool
 *     boundaries in reactive pipelines without manual lifting.
 *  2. Registers {@link TenantMdcAccessor} so tenantId/userId appear in MDC on
 *     every log line, including those on Schedulers.parallel() threads.
 *
 * Zipkin / Brave configuration (sampling rate, endpoint) is handled by
 * Spring Boot's autoconfigure via management.tracing.* in application.yml.
 */
@Configuration
public class TracingConfiguration {

    /**
     * Registers the TenantMdcAccessor with Micrometer's ContextRegistry.
     * Must happen before any reactive pipelines are subscribed.
     */
    @Bean
    public TenantMdcAccessor tenantMdcAccessor() {
        TenantMdcAccessor accessor = new TenantMdcAccessor();
        ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);
        return accessor;
    }

    /**
     * Enables Reactor's automatic context propagation bridge.
     * After this call, Reactor will call ThreadLocalAccessor.setValue/reset
     * whenever the reactive pipeline crosses a thread boundary.
     */
    @PostConstruct
    public void enableReactorContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
    }
}
