package com.modus.license.observability.config;

import com.modus.license.observability.filter.MdcEnrichmentFilter;
import com.modus.license.observability.filter.ReactiveMdcWebFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for platform observability.
 *
 * Activated automatically when platform-observability is on the classpath.
 * Imports {@link MetricsConfiguration} and {@link TracingConfiguration},
 * then registers the appropriate MDC enrichment filter based on web stack.
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@Import({MetricsConfiguration.class, TracingConfiguration.class})
public class PlatformObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "platform.observability", name = "mdc-enabled", matchIfMissing = true)
    public MdcEnrichmentFilter mdcEnrichmentFilter(
            @Value("${spring.application.name:unknown}") String serviceName,
            @Value("${platform.region:eastus}") String region
    ) {
        return new MdcEnrichmentFilter(serviceName, region);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnProperty(prefix = "platform.observability", name = "mdc-enabled", matchIfMissing = true)
    public ReactiveMdcWebFilter reactiveMdcWebFilter(
            @Value("${spring.application.name:unknown}") String serviceName,
            @Value("${platform.region:eastus}") String region
    ) {
        return new ReactiveMdcWebFilter(serviceName, region);
    }
}
