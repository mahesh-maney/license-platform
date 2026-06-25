package com.modus.license.subscription.api.dto;

import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.subscription.domain.enums.BillingCycle;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreatePlanRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        @NotNull
        PlanTier tier,

        @NotNull
        LicenseType licenseType,

        /** Null = unlimited seats (e.g. SITE licence). */
        @Min(1)
        Integer maxSeats,

        @NotNull
        BillingCycle billingCycle,

        @NotNull
        @Min(0)
        Long priceInCents,

        @Size(min = 3, max = 3)
        String currency,

        Set<String> features
) {
    public CreatePlanRequest {
        currency  = (currency == null) ? "USD" : currency;
        features  = (features == null) ? Set.of() : Set.copyOf(features);
    }
}
