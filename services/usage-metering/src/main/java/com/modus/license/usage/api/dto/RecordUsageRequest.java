package com.modus.license.usage.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Allows services and SDK clients to directly record metered usage
 * without going through a Kafka event.
 */
public record RecordUsageRequest(

        UUID userId,        // optional — null for system-generated usage

        @NotBlank(message = "featureKey is required")
        String featureKey,

        @NotBlank(message = "metricName is required")
        String metricName,

        @Min(value = 0, message = "quantity must be non-negative")
        double quantity,

        @NotBlank(message = "unit is required")
        String unit
) {}
