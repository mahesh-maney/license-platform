package com.modus.license.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the {@code session.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "session")
public record SessionProperties(
        long ttlSeconds,
        long heartbeatIntervalSeconds,
        long maxConcurrentDefault,
        long cleanupIntervalSeconds
) {}
