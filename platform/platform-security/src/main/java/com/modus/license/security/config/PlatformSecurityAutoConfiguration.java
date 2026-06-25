package com.modus.license.security.config;

import com.modus.license.security.filter.TenantContextFilter;
import com.modus.license.security.filter.TenantContextWebFilter;
import com.modus.license.security.jwt.JwtClaimsExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for platform-security.
 *
 * Activated automatically when platform-security is on the classpath.
 * Registers the JWT claim extractor and the appropriate tenant context filter
 * based on whether the service is reactive (WebFlux) or blocking (Servlet).
 *
 * Each service is still responsible for defining its own SecurityFilterChain /
 * SecurityWebFilterChain with service-specific path authorization rules.
 */
@AutoConfiguration
@EnableConfigurationProperties(PlatformSecurityProperties.class)
public class PlatformSecurityAutoConfiguration {

    @Bean
    public JwtClaimsExtractor jwtClaimsExtractor(PlatformSecurityProperties props) {
        return new JwtClaimsExtractor(props);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public TenantContextWebFilter tenantContextWebFilter(JwtClaimsExtractor extractor) {
        return new TenantContextWebFilter(extractor);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public TenantContextFilter tenantContextFilter(JwtClaimsExtractor extractor) {
        return new TenantContextFilter(extractor);
    }
}
