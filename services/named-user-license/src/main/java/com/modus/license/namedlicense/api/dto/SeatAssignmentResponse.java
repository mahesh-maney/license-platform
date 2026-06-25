package com.modus.license.namedlicense.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SeatAssignmentResponse(
        UUID id,
        UUID licenseId,
        UUID userId,
        String email,
        Instant assignedAt,
        String assignedBy
) {}
