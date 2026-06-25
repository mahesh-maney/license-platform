package com.modus.license.reporting.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionSnapshotResponse(
        UUID    subscriptionId,
        UUID    tenantId,
        UUID    planId,
        String  planTier,
        String  licenseType,
        String  billingCycle,
        Integer seatLimit,
        String  status,
        Instant startDate,
        Instant endDate,
        Instant recordedAt
) {}
