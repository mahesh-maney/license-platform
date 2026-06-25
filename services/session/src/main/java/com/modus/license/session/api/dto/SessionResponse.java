package com.modus.license.session.api.dto;

import java.time.Instant;

public record SessionResponse(
        String sessionId,
        String tenantId,
        String userId,
        String licenseId,
        String clientIp,
        String userAgent,
        Instant startedAt,
        Instant lastSeenAt
) {}
