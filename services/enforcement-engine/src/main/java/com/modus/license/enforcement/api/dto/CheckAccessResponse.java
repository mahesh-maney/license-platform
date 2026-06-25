package com.modus.license.enforcement.api.dto;

public record CheckAccessResponse(
        boolean allowed,
        String decision,          // ALLOWED | DENIED
        String denialReason,      // null when allowed
        String entitlementId,     // null when denied
        long responseTimeMs,
        boolean cacheHit
) {}
