package com.modus.license.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID   id,
        UUID   tenantId,
        String actorId,
        String actorType,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String serviceName,
        String ipAddress,
        String userAgent,
        String requestId,
        String failureReason,
        Instant recordedAt,
        String contentHash
) {}
