package com.modus.license.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provides the {@link KeyResolver} bean consumed by the {@code RequestRateLimiter}
 * gateway filter declared in {@code application.yml}.
 *
 * <p>Rate limit key: authenticated user name (JWT subject). Anonymous requests
 * (unauthenticated paths like /actuator/health) fall back to the literal
 * {@code "anonymous"}, sharing a single anonymous bucket.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(java.security.Principal::getName)
                .defaultIfEmpty("anonymous");
    }
}
