package com.modus.license.entitlement.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Serialized to Redis as JSON for the entitlement cache.
 * {@code @JsonIgnoreProperties} tolerates forward-compatible cache entries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntitlementResponse(
        UUID id,
        UUID tenantId,
        UUID subscriptionId,
        UUID planId,
        PlanTier planTier,
        LicenseType licenseType,
        Integer seatLimit,
        Set<String> featureKeys,
        EntitlementStatus status,
        Instant startDate,
        Instant endDate,
        Instant createdAt,
        Instant updatedAt
) {}
