package com.modus.license.tenant.api.dto;

import com.modus.license.core.domain.enums.PlanTier;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a tenant — all fields are optional (patch semantics).
 * Null means "do not change this field".
 */
public record UpdateTenantRequest(

        @Size(min = 1, max = 255)
        String name,

        @Size(max = 255)
        String displayName,

        PlanTier planTier,

        @Size(max = 50)
        String region
) {}
