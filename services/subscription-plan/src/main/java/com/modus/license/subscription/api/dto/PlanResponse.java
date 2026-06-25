package com.modus.license.subscription.api.dto;

import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.subscription.domain.enums.BillingCycle;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        String description,
        PlanTier tier,
        LicenseType licenseType,
        Integer maxSeats,
        BillingCycle billingCycle,
        Long priceInCents,
        String currency,
        boolean active,
        Set<String> features,
        Instant createdAt,
        Instant updatedAt
) {}
