package com.modus.license.audit.config;

import com.modus.license.audit.aspect.AuditableAspect;
import com.modus.license.audit.publisher.AuditEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Auto-configuration for platform-audit.
 *
 * Activated automatically when platform-audit is on the classpath and a
 * {@link KafkaTemplate} bean is available (all services have one).
 *
 * Registers:
 * <ul>
 *   <li>{@link AuditEventPublisher} — Kafka publisher to modus.audit.events</li>
 *   <li>{@link AuditableAspect} — AOP interceptor for {@literal @}Auditable methods</li>
 * </ul>
 *
 * {@code @EnableAspectJAutoProxy} is required here so the aspect is active
 * even if the service does not declare it itself.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
public class PlatformAuditAutoConfiguration {

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean
    public AuditEventPublisher auditEventPublisher(
            @SuppressWarnings("unchecked") KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new AuditEventPublisher(kafkaTemplate);
    }

    @Bean
    @ConditionalOnBean(AuditEventPublisher.class)
    @ConditionalOnMissingBean
    public AuditableAspect auditableAspect(
            AuditEventPublisher publisher,
            @Value("${spring.application.name:unknown}") String serviceName
    ) {
        return new AuditableAspect(publisher, serviceName);
    }
}
