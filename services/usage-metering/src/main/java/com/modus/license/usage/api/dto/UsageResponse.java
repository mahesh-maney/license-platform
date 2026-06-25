package com.modus.license.usage.api.dto;

import java.time.Instant;

public record UsageResponse(
        String usageId,
        String tenantId,
        String userId,
        String featureKey,
        String metricName,
        double quantity,
        String unit,
        Instant recordedAt
) {}
