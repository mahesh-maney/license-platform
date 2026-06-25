package com.modus.license.feature.api.dto;

import com.modus.license.core.domain.enums.FeatureStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Set or update a per-tenant feature override.
 * PLATFORM_ADMIN may set any status; TENANT_ADMIN may only set ENABLED or DISABLED.
 */
public record SetTenantFeatureRequest(

        @NotNull
        FeatureStatus status,

        /** Optional tenant-specific JSON configuration for this feature. */
        String configJson
) {}
