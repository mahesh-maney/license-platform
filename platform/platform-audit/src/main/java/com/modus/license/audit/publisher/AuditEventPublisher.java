package com.modus.license.audit.publisher;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Publishes {@link AuditEvent} records to the {@code modus.audit.events} Kafka topic.
 *
 * Audit publication is fire-and-forget: failures are logged but never propagated
 * to the caller. The main business flow must never be broken by audit errors.
 *
 * Partition key: tenantId — keeps all audit events for a tenant on the same partition,
 * enabling ordered reads per tenant in the audit service consumer.
 */
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuditEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes an audit event synchronously (blocking services).
     * Exceptions are caught and logged — never thrown to the caller.
     */
    public void publish(AuditEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.AUDIT_EVENTS, event.getTenantId(), event);
            log.debug("Audit event published: action={} resource={}/{}",
                    event.getAction(), event.getResourceType(), event.getResourceId());
        } catch (Exception e) {
            log.error("Failed to publish audit event: action={} resource={}/{}. Error: {}",
                    event.getAction(), event.getResourceType(), event.getResourceId(), e.getMessage());
        }
    }

    /**
     * Publishes an audit event asynchronously (reactive services).
     * Returns an empty Mono that completes after the send is submitted.
     * Errors are caught and logged — the returned Mono never errors.
     */
    public Mono<Void> publishAsync(AuditEvent event) {
        return Mono.fromRunnable(() -> publish(event))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
