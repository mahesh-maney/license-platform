package com.modus.license.feature.api.dto;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;

import java.util.UUID;

/**
 * Effective feature state for a specific tenant.
 *
 * {@code effectiveStatus} is the resolved status: override wins over global.
 * {@code overriddenStatus} is present only if a tenant override exists.
 */
public record TenantFeatureResponse(
        UUID tenantId,
        String featureKey,
        String name,
        PlanTier minimumPlanTier,
        FeatureStatus globalStatus,
        FeatureStatus overriddenStatus,
        FeatureStatus effectiveStatus,
        String configJson,
        boolean accessible
) {}
