package com.modus.license.feature.api.dto;

import com.modus.license.core.domain.enums.PlanTier;
import jakarta.validation.constraints.Size;

/** Patch-style update — null fields are left unchanged. featureKey is immutable. */
public record UpdateFeatureRequest(

        @Size(max = 200)
        String name,

        @Size(max = 500)
        String description,

        PlanTier minimumPlanTier,

        String configSchemaJson
) {}
