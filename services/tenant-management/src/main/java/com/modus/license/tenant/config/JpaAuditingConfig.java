package com.modus.license.tenant.config;

import com.modus.license.core.context.TenantContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    /**
     * Provides the current user ID for @CreatedBy / @LastModifiedBy JPA audit fields.
     * Reads from TenantContextHolder which is populated by TenantContextFilter
     * (platform-security auto-configuration).
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(TenantContextHolder.get())
                .map(ctx -> ctx.userId().toString());
    }
}
