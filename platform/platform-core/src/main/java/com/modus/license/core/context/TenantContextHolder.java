package com.modus.license.core.context;

import java.util.Optional;

/**
 * ThreadLocal-based holder for blocking (JPA/servlet) services.
 *
 * Reactive (WebFlux/R2DBC) services must use Reactor Context instead
 * of this holder — see TenantContextFilter in platform-security.
 *
 * Always call clear() in a finally block or filter teardown to
 * prevent context leaks across thread pool reuse.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> HOLDER = new InheritableThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    public static TenantContext get() {
        return HOLDER.get();
    }

    public static Optional<TenantContext> getOptional() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static TenantContext require() {
        TenantContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException(
                "TenantContext is not set on the current thread. " +
                "Ensure the security filter has populated the context before this call."
            );
        }
        return ctx;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
