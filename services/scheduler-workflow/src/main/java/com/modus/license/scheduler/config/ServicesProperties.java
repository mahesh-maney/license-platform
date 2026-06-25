package com.modus.license.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServicesProperties(
        Downstream subscription,
        Downstream entitlement,
        Downstream notification
) {

    public record Downstream(String url) {}
}
