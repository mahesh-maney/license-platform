package com.modus.license.reporting.config;

import com.modus.license.core.context.TenantContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // Usage metrics are written by the Kafka consumer (no user context), so fall back to "system"
        return () -> Optional.ofNullable(TenantContextHolder.get())
                .map(ctx -> ctx.userId().toString())
                .or(() -> Optional.of("system"));
    }
}
