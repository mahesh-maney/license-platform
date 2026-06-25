package com.modus.license.subscription.api.dto;

import com.modus.license.subscription.domain.enums.BillingCycle;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateSubscriptionRequest(

        @NotNull
        UUID planId,

        @NotNull
        BillingCycle billingCycle,

        /** Override the plan's default seat limit. Null = use plan default. */
        @Min(1)
        Integer seatLimit,

        @NotNull
        Instant startDate,

        /** Null = evergreen (auto-renewing). */
        Instant endDate
) {}
