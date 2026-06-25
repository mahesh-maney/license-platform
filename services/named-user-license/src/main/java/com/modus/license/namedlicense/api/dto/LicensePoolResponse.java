package com.modus.license.namedlicense.api.dto;

import java.time.Instant;
import java.util.UUID;

public record LicensePoolResponse(
        UUID id,
        UUID tenantId,
        UUID planId,
        UUID entitlementId,
        int totalSeats,
        int usedSeats,
        int availableSeats,
        Instant createdAt,
        Instant updatedAt
) {}
