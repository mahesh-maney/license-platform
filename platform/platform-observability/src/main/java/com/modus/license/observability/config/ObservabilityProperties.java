package com.modus.license.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform-level observability settings.
 *
 * Complements Spring Boot's built-in management.* and spring.application.*
 * properties with platform-specific knobs.
 *
 * Example:
 * platform:
 *   region: eastus
 *   observability:
 *     mdc-enabled: true
 */
@ConfigurationProperties(prefix = "platform.observability")
public class ObservabilityProperties {

    /** Whether to enrich MDC with tenantId/userId on every request. Default: true. */
    private boolean mdcEnabled = true;

    public boolean isMdcEnabled() { return mdcEnabled; }
    public void setMdcEnabled(boolean mdcEnabled) { this.mdcEnabled = mdcEnabled; }
}
