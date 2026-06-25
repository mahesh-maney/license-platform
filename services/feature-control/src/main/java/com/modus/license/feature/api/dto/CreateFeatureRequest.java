package com.modus.license.feature.api.dto;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFeatureRequest(

        /**
         * Stable code-level key. SCREAMING_SNAKE_CASE, max 100 chars.
         * Must never change after creation.
         */
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]{2,100}$",
                 message = "featureKey must be SCREAMING_SNAKE_CASE (A-Z, 0-9, underscore), 2-100 chars")
        String featureKey,

        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 500)
        String description,

        /** Null = available to all plan tiers. */
        PlanTier minimumPlanTier,

        @NotNull
        FeatureStatus status,

        String configSchemaJson
) {}
