package com.modus.license.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the {@code audit.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "audit")
public record AuditProperties(
        String hashAlgorithm,
        boolean wormEnabled,
        long retentionDays
) {}
