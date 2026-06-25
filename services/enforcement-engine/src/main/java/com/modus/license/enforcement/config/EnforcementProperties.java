package com.modus.license.enforcement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the {@code enforcement.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "enforcement")
public record EnforcementProperties(
        boolean cacheWarmOnStartup,
        long decisionTimeoutMs,
        boolean cacheMissFallbackDb
) {}
