package com.modus.license.reporting.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UsageMetricResponse(
        UUID    id,
        UUID    tenantId,
        String  featureKey,
        String  metricName,
        String  unit,
        Instant windowStart,
        Instant windowEnd,
        double  totalQuantity,
        long    eventCount,
        int     thresholdBreaches
) {}
