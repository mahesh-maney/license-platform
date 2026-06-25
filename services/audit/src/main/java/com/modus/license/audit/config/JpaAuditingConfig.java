package com.modus.license.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing for @CreatedDate / @LastModifiedDate on entities.
 * Audit log records are written-once, so AuditorAware is not needed here.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
