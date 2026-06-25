package com.modus.license.enforcement.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckAccessRequest(

        @NotNull(message = "userId is required")
        UUID userId,

        @NotBlank(message = "featureKey is required")
        String featureKey,

        String sessionId  // optional — present for concurrent-license enforcement
) {}
