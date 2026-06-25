package com.modus.license.security.rbac;

/**
 * Canonical role names used across all Modus services.
 *
 * These values must match what Keycloak (or Azure AD) emits in the JWT roles claim.
 *
 * Use with Spring Security's hasAuthority():
 *   .requestMatchers("/admin/**").hasAuthority(ModusRole.PLATFORM_ADMIN)
 *
 * Or with TenantContext.hasRole():
 *   ctx.hasRole(ModusRole.TENANT_ADMIN)
 */
public final class ModusRole {

    private ModusRole() {}

    /** Super-admin with cross-tenant access. Internal platform operations only. */
    public static final String PLATFORM_ADMIN   = "PLATFORM_ADMIN";

    /** Administrator within a single tenant. Can manage users, licenses, settings. */
    public static final String TENANT_ADMIN     = "TENANT_ADMIN";

    /** Standard end-user within a tenant. Subject to license enforcement. */
    public static final String TENANT_USER      = "TENANT_USER";

    /** Can view and manage billing, subscriptions, and invoices for a tenant. */
    public static final String BILLING_ADMIN    = "BILLING_ADMIN";

    /** Read-only access across all resources within a tenant. */
    public static final String READONLY         = "READONLY";

    /** Machine-to-machine service account. Used by internal services. */
    public static final String SERVICE_ACCOUNT  = "SERVICE_ACCOUNT";

    /** Grants access to license assignment and revocation operations. */
    public static final String LICENSE_MANAGER  = "LICENSE_MANAGER";
}
