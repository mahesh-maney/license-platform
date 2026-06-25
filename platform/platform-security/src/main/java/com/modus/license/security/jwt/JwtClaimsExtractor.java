package com.modus.license.security.jwt;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.exception.TenantContextException;
import com.modus.license.security.config.PlatformSecurityProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts a {@link TenantContext} from a validated Spring Security {@link Jwt}.
 *
 * Reads claim names from {@link PlatformSecurityProperties} so they can be
 * overridden per service without changing code.
 */
public class JwtClaimsExtractor {

    private final PlatformSecurityProperties props;

    public JwtClaimsExtractor(PlatformSecurityProperties props) {
        this.props = props;
    }

    public TenantContext extract(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(props.getClaimTenantId());
        String userId   = jwt.getClaimAsString(props.getClaimUserId());

        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextException(
                "JWT is missing the tenant identity claim '" + props.getClaimTenantId() + "'. " +
                "Ensure Keycloak / Azure AD is configured to include this claim."
            );
        }
        if (userId == null || userId.isBlank()) {
            throw new TenantContextException(
                "JWT is missing the user identity claim '" + props.getClaimUserId() + "'."
            );
        }

        List<String> rawRoles = jwt.getClaimAsStringList(props.getClaimRoles());
        Set<String> roles = rawRoles != null ? new HashSet<>(rawRoles) : Set.of();

        return TenantContext.of(tenantId, userId, roles);
    }
}
