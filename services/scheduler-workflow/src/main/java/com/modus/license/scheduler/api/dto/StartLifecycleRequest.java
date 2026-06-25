package com.modus.license.scheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/scheduler/subscription-lifecycle}.
 *
 * <p>Called by the subscription-plan service when a subscription is activated
 * or renewed to register it with the lifecycle workflow engine.
 */
public record StartLifecycleRequest(
        @NotNull  UUID    tenantId,
        @NotBlank String  subscriptionId,
        @NotBlank String  planId,
        @NotBlank String  planTier,
        @NotBlank String  licenseType,
        @NotBlank String  billingCycle,
                  Integer seatLimit,
        @NotNull  Instant startDate,
                  Instant endDate          // null = evergreen subscription
) {}
