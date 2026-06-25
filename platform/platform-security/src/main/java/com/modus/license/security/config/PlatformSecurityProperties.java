package com.modus.license.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds to the platform.jwt.* block present in every service's application.yml.
 *
 * Example:
 * platform:
 *   jwt:
 *     claim-tenant-id: tenant_id
 *     claim-user-id:   sub
 *     claim-roles:     roles
 */
@ConfigurationProperties(prefix = "platform.jwt")
public class PlatformSecurityProperties {

    /** JWT claim that contains the tenant UUID. */
    private String claimTenantId = "tenant_id";

    /** JWT claim that contains the user UUID (standard OIDC subject). */
    private String claimUserId = "sub";

    /** JWT claim that contains the list of role strings. */
    private String claimRoles = "roles";

    public String getClaimTenantId() { return claimTenantId; }
    public void setClaimTenantId(String claimTenantId) { this.claimTenantId = claimTenantId; }

    public String getClaimUserId() { return claimUserId; }
    public void setClaimUserId(String claimUserId) { this.claimUserId = claimUserId; }

    public String getClaimRoles() { return claimRoles; }
    public void setClaimRoles(String claimRoles) { this.claimRoles = claimRoles; }
}
