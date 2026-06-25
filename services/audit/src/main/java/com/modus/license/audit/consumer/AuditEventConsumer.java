package com.modus.license.audit.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.audit.archive.AuditArchiveService;
import com.modus.license.audit.config.AuditProperties;
import com.modus.license.audit.domain.entity.AuditLogEntity;
import com.modus.license.audit.domain.repository.AuditLogRepository;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes {@link AuditEvent} records from {@code modus.audit.events},
 * persists them to PostgreSQL, and optionally archives to Azure WORM storage.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditLogRepository repository;
    private final AuditProperties    props;
    private final Optional<AuditArchiveService> archiveService;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditLogRepository repository,
                               AuditProperties props,
                               Optional<AuditArchiveService> archiveService) {
        this.repository     = repository;
        this.props          = props;
        this.archiveService = archiveService;
        this.objectMapper   = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @KafkaListener(
            topics = TopicConstants.AUDIT_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAuditEvent(AuditEvent event) {
        try {
            UUID auditId = UUID.fromString(event.getAuditId());

            // Idempotency — skip if already persisted (Kafka delivery guarantee is at-least-once)
            if (repository.existsById(auditId)) {
                log.debug("Skipping duplicate audit event: auditId={}", auditId);
                return;
            }

            AuditLogEntity entity = toEntity(event, auditId);
            repository.save(entity);
            log.debug("Persisted audit log: auditId={} tenant={} action={} resource={}",
                    auditId, event.getTenantId(), event.getAction(), event.getResourceType());

            archiveService.ifPresent(svc -> {
                try {
                    String json = objectMapper.writeValueAsString(entity);
                    svc.archive(entity, json);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialise audit record for archival: {}", e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Failed to process audit event: auditId={} error={}",
                    event.getAuditId(), e.getMessage(), e);
            throw new RuntimeException("Audit event processing failed", e); // triggers Kafka retry
        }
    }

    private AuditLogEntity toEntity(AuditEvent event, UUID auditId) {
        AuditLogEntity e = new AuditLogEntity();
        e.setId(auditId);
        e.setTenantId(UUID.fromString(event.getTenantId()));
        e.setActorId(event.getActorId());
        e.setActorType(event.getActorType());
        e.setAction(event.getAction());
        e.setResourceType(event.getResourceType());
        e.setResourceId(event.getResourceId());
        e.setOutcome(event.getOutcome());
        e.setServiceName(event.getServiceName());
        e.setIpAddress(event.getIpAddress());
        e.setUserAgent(event.getUserAgent());
        e.setRequestId(event.getRequestId());
        e.setBeforeState(event.getBefore());
        e.setAfterState(event.getAfter());
        e.setFailureReason(event.getFailureReason());
        e.setRecordedAt(event.getMetadata().getTimestamp());
        e.setContentHash(computeHash(event));
        return e;
    }

    private String computeHash(AuditEvent event) {
        String content = event.getTenantId()
                + event.getActorId()
                + event.getAction()
                + event.getResourceType()
                + event.getResourceId()
                + event.getOutcome()
                + event.getMetadata().getTimestamp().toString();
        try {
            MessageDigest digest = MessageDigest.getInstance(props.hashAlgorithm());
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Hash algorithm not available: " + props.hashAlgorithm(), ex);
        }
    }
}
