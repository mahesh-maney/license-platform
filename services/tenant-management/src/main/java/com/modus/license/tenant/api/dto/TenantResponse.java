package com.modus.license.tenant.api.dto;

import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.domain.enums.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String slug,
        String name,
        String displayName,
        String adminEmail,
        TenantStatus status,
        PlanTier planTier,
        String region,
        Instant trialEndsAt,
        Instant createdAt,
        Instant updatedAt
) {}
