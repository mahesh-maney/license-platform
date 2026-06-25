package com.modus.license.session.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Serialised to JSON and stored in Redis at key {@code session:{sessionId}} with a TTL.
 * Also tracked in the per-tenant active set at {@code session:active:{tenantId}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionRecord(
        String sessionId,
        String tenantId,
        String userId,
        String licenseId,   // nullable
        String clientIp,    // nullable
        String userAgent,   // nullable
        Instant startedAt,
        Instant lastSeenAt
) {}
