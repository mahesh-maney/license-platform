package com.modus.license.reporting.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EntitlementSnapshotResponse(
        UUID         entitlementId,
        UUID         tenantId,
        UUID         subscriptionId,
        UUID         planId,
        String       planTier,
        String       licenseType,
        Integer      seatLimit,
        List<String> featureKeys,
        String       status,
        Instant      startDate,
        Instant      endDate,
        Instant      recordedAt
) {}
