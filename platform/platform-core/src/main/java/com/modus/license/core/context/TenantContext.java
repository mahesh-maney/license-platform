package com.modus.license.core.context;

import com.modus.license.core.domain.id.TenantId;
import com.modus.license.core.domain.id.UserId;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable context carried on every request: who the tenant is,
 * which user is acting, and what roles they hold.
 *
 * Propagated via TenantContextHolder (ThreadLocal for blocking services)
 * or via Reactor Context (reactive services — see platform-security).
 */
public record TenantContext(
        TenantId tenantId,
        UserId userId,
        Set<String> roles
) {

    public static final String REACTOR_CONTEXT_KEY = "modus.tenant.context";

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId,   "userId must not be null");
        roles = roles == null ? Collections.emptySet() : Collections.unmodifiableSet(roles);
    }

    public static TenantContext of(TenantId tenantId, UserId userId, Set<String> roles) {
        return new TenantContext(tenantId, userId, roles);
    }

    public static TenantContext of(String tenantId, String userId, Set<String> roles) {
        return new TenantContext(TenantId.of(tenantId), UserId.of(userId), roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... required) {
        for (String role : required) {
            if (roles.contains(role)) return true;
        }
        return false;
    }
}
