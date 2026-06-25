package com.modus.license.feature.api.dto;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;

import java.time.Instant;
import java.util.UUID;

public record FeatureResponse(
        UUID id,
        String featureKey,
        String name,
        String description,
        PlanTier minimumPlanTier,
        FeatureStatus status,
        String configSchemaJson,
        Instant createdAt,
        Instant updatedAt
) {}
