package com.modus.license.test.context;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.core.domain.id.TenantId;
import com.modus.license.core.domain.id.UserId;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Test utility for setting up {@link TenantContext} in both blocking and reactive tests.
 *
 * <h3>Blocking (JPA/servlet) tests:</h3>
 * <pre>
 *   {@literal @}BeforeEach
 *   void setup() { TenantContextTestHelper.setDefault(); }
 *
 *   {@literal @}AfterEach
 *   void teardown() { TenantContextTestHelper.clear(); }
 * </pre>
 *
 * <h3>Reactive (WebFlux/R2DBC) tests:</h3>
 * <pre>
 *   StepVerifier.create(
 *       service.doSomething()
 *              .contextWrite(TenantContextTestHelper.reactorContext())
 *   ).expectNextCount(1).verifyComplete();
 * </pre>
 *
 * <h3>WebTestClient (WebFlux slice) tests:</h3>
 * Use {@link com.modus.license.test.context.WithTenantContext} annotation to
 * inject context via a test SecurityContext mock.
 */
public final class TenantContextTestHelper {

    // Default test tenant / user UUIDs — stable across all test classes
    public static final TenantId DEFAULT_TENANT_ID =
            TenantId.of("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UserId DEFAULT_USER_ID =
            UserId.of("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final TenantId SECOND_TENANT_ID =
            TenantId.of("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private TenantContextTestHelper() {}

    /** Default context: TENANT_ADMIN role. */
    public static TenantContext defaultContext() {
        return TenantContext.of(DEFAULT_TENANT_ID, DEFAULT_USER_ID, Set.of("TENANT_ADMIN"));
    }

    /** Context with PLATFORM_ADMIN role (cross-tenant operations). */
    public static TenantContext platformAdminContext() {
        return TenantContext.of(DEFAULT_TENANT_ID, DEFAULT_USER_ID, Set.of("PLATFORM_ADMIN"));
    }

    /** Context with minimal TENANT_USER role. */
    public static TenantContext tenantUserContext() {
        return TenantContext.of(DEFAULT_TENANT_ID, DEFAULT_USER_ID, Set.of("TENANT_USER"));
    }

    /** Custom context for specific tenant/user combinations. */
    public static TenantContext of(TenantId tenantId, UserId userId, String... roles) {
        return TenantContext.of(tenantId, userId, Set.of(roles));
    }

    // ── Blocking helpers ──────────────────────────────────────────────────────

    /** Sets the default TenantContext on TenantContextHolder (blocking tests). */
    public static void setDefault() {
        TenantContextHolder.set(defaultContext());
    }

    /** Sets a specific TenantContext on TenantContextHolder (blocking tests). */
    public static void set(TenantContext ctx) {
        TenantContextHolder.set(ctx);
    }

    /** Clears TenantContextHolder — call in @AfterEach to prevent leaks. */
    public static void clear() {
        TenantContextHolder.clear();
    }

    /**
     * Runs the supplier with the default TenantContext set, then clears.
     * Useful for one-off operations in setup methods.
     */
    public static <T> T runWith(TenantContext ctx, Supplier<T> supplier) {
        TenantContextHolder.set(ctx);
        try {
            return supplier.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    // ── Reactive helpers ──────────────────────────────────────────────────────

    /**
     * Returns a Reactor {@link Context} with the default TenantContext.
     * Use with {@code .contextWrite(TenantContextTestHelper.reactorContext())}.
     */
    public static Context reactorContext() {
        return reactorContext(defaultContext());
    }

    /**
     * Returns a Reactor {@link Context} with the given TenantContext.
     */
    public static Context reactorContext(TenantContext ctx) {
        return Context.of(TenantContext.REACTOR_CONTEXT_KEY, ctx);
    }

    /**
     * Wraps a Mono with the default TenantContext in Reactor Context.
     * Convenience for: {@code mono.contextWrite(TenantContextTestHelper.reactorContext())}.
     */
    public static <T> Mono<T> withContext(Mono<T> mono) {
        return mono.contextWrite(reactorContext());
    }

    /**
     * Wraps a Mono with a specific TenantContext in Reactor Context.
     */
    public static <T> Mono<T> withContext(Mono<T> mono, TenantContext ctx) {
        return mono.contextWrite(reactorContext(ctx));
    }
}
