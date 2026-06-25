package com.modus.license.enforcement.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Entitlement data cached in Redis for fast enforcement decisions.
 *
 * Stored as JSON at key {@code enforcement:entitlement:{tenantId}}.
 * Warmed and invalidated by consuming {@code modus.entitlement.events}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CachedEntitlement(
        String entitlementId,
        String tenantId,
        String licenseType,       // NAMED_USER | CONCURRENT | FEATURE_BASED
        String status,            // ACTIVE | EXPIRED | SUSPENDED | REVOKED | PENDING
        Integer seatLimit,        // null for feature-based licenses
        List<String> featureKeys, // features this entitlement grants
        String planTier,
        Instant effectiveAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean grantsFeature(String featureKey) {
        return featureKeys != null && featureKeys.contains(featureKey);
    }

    public boolean isConcurrent() {
        return "CONCURRENT".equals(licenseType);
    }
}
