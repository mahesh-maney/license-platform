package com.modus.license.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tags every metric emitted by the service with common platform dimensions,
 * making cross-service dashboards and alerts possible in Prometheus/Grafana.
 *
 * Tags added to every metric:
 *   service  → spring.application.name
 *   region   → platform.region (Azure region)
 */
@Configuration
public class MetricsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> platformCommonTags(
            @Value("${spring.application.name:unknown}") String serviceName,
            @Value("${platform.region:eastus}") String region
    ) {
        return registry -> registry.config()
                .commonTags(
                        "service", serviceName,
                        "region",  region
                );
    }

    /**
     * Deny high-cardinality URI tags that would explode the time-series count.
     * Specific services can add further filters on top.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> highCardinalityDenyList() {
        return registry -> registry.config()
                .meterFilter(MeterFilter.deny(id -> {
                    String uri = id.getTag("uri");
                    return uri != null && (uri.contains("actuator") || uri.contains("swagger"));
                }));
    }
}
