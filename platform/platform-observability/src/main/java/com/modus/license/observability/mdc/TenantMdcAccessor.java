package com.modus.license.observability.mdc;

import com.modus.license.core.context.TenantContext;
import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * Bridges {@link TenantContext} from Reactor Context into SLF4J MDC
 * via Micrometer's Context Propagation API.
 *
 * When {@code Hooks.enableAutomaticContextPropagation()} is called at startup,
 * Reactor automatically calls {@link #setValue} / {@link #reset} as the reactive
 * pipeline crosses thread boundaries, keeping MDC tenantId and userId consistent
 * across Schedulers (boundedElastic, parallel, etc.).
 *
 * Keyed on {@link TenantContext#REACTOR_CONTEXT_KEY} so it reads from the same
 * Reactor Context slot written by {@code TenantContextWebFilter}.
 */
public class TenantMdcAccessor implements ThreadLocalAccessor<TenantContext> {

    @Override
    public Object key() {
        return TenantContext.REACTOR_CONTEXT_KEY;
    }

    @Override
    public TenantContext getValue() {
        // Not used for restoration — Reactor Context is the source of truth
        return null;
    }

    @Override
    public void setValue(TenantContext ctx) {
        if (ctx != null) {
            MDC.put(MdcKeys.TENANT_ID, ctx.tenantId().toString());
            MDC.put(MdcKeys.USER_ID,   ctx.userId().toString());
        }
    }

    @Override
    public void setValue() {
        MDC.remove(MdcKeys.TENANT_ID);
        MDC.remove(MdcKeys.USER_ID);
    }

    @Override
    public void reset() {
        setValue();
    }

    @Override
    public void restore() {
        setValue();
    }
}
