package com.modus.license.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID   id,
        UUID   tenantId,
        String notificationType,
        String channel,
        String recipientEmail,
        String subject,
        String status,
        int    retryCount,
        String failureReason,
        Instant sentAt,
        Instant createdAt
) {}
