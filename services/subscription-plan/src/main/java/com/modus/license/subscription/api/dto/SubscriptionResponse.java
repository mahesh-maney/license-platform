package com.modus.license.subscription.api.dto;

import com.modus.license.subscription.domain.enums.BillingCycle;
import com.modus.license.subscription.domain.enums.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        PlanResponse plan,
        SubscriptionStatus status,
        Integer seatLimit,
        Integer effectiveSeatLimit,
        BillingCycle billingCycle,
        Instant startDate,
        Instant endDate,
        String cancellationReason,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {}
