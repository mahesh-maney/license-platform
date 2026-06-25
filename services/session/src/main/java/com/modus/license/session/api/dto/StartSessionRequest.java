package com.modus.license.session.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartSessionRequest(

        @NotNull(message = "userId is required")
        UUID userId,

        UUID licenseId,   // optional — present for named-user license sessions

        String clientIp,  // optional — extracted from request if omitted

        String userAgent  // optional
) {}
