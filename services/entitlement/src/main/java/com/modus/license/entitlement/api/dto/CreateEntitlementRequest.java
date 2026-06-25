package com.modus.license.entitlement.api.dto;

import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Manual entitlement grant used by PLATFORM_ADMIN for bespoke arrangements. */
public record CreateEntitlementRequest(

        @NotNull UUID tenantId,
        @NotNull UUID subscriptionId,
        @NotNull UUID planId,
        @NotNull PlanTier planTier,
        @NotNull LicenseType licenseType,

        @Min(1) Integer seatLimit,

        Set<String> featureKeys,

        @NotNull Instant startDate,
        Instant endDate
) {
    public CreateEntitlementRequest {
        featureKeys = (featureKeys == null) ? Set.of() : Set.copyOf(featureKeys);
    }
}
