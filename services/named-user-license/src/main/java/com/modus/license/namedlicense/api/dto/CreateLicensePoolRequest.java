package com.modus.license.namedlicense.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateLicensePoolRequest(

        @NotNull(message = "planId is required")
        UUID planId,

        @NotNull(message = "entitlementId is required")
        UUID entitlementId,

        @Min(value = 1, message = "totalSeats must be at least 1")
        int totalSeats
) {}
