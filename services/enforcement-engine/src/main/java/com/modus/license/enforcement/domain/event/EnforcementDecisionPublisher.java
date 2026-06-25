package com.modus.license.enforcement.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.enforcement.EnforcementDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link EnforcementDecision} Avro records to {@code modus.enforcement.decisions}.
 * Partition key: tenantId.
 */
@Component
public class EnforcementDecisionPublisher {

    private static final Logger log = LoggerFactory.getLogger(EnforcementDecisionPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EnforcementDecisionPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishAllowed(String tenantId, String userId, String featureKey,
                                String licenseType, String entitlementId,
                                String sessionId, long responseTimeMs, boolean cacheHit) {
        publish(build(tenantId, userId, featureKey, licenseType,
                EventTypes.Enforcement.ALLOWED, null,
                entitlementId, sessionId, responseTimeMs, cacheHit));
    }

    public void publishDenied(String tenantId, String userId, String featureKey,
                               String licenseType, String denialReason,
                               String sessionId, long responseTimeMs, boolean cacheHit) {
        publish(build(tenantId, userId, featureKey, licenseType,
                EventTypes.Enforcement.DENIED, denialReason,
                null, sessionId, responseTimeMs, cacheHit));
    }

    private EnforcementDecision build(String tenantId, String userId, String featureKey,
                                       String licenseType, String decision,
                                       String denialReason, String entitlementId,
                                       String sessionId, long responseTimeMs, boolean cacheHit) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(decision)
                .setTenantId(tenantId)
                .setActorId(userId)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return EnforcementDecision.newBuilder()
                .setMetadata(metadata)
                .setDecisionId(UUID.randomUUID().toString())
                .setTenantId(tenantId)
                .setUserId(userId)
                .setFeatureKey(featureKey)
                .setLicenseType(licenseType)
                .setDecision(decision)
                .setDenialReason(denialReason)
                .setEntitlementId(entitlementId)
                .setSessionId(sessionId)
                .setResponseTimeMs(responseTimeMs)
                .setCacheHit(cacheHit)
                .build();
    }

    private void publish(EnforcementDecision event) {
        try {
            kafkaTemplate.send(TopicConstants.ENFORCEMENT_DECISIONS,
                    event.getTenantId(), event);
            log.debug("Published enforcement decision: decision={} tenantId={} featureKey={}",
                    event.getDecision(), event.getTenantId(), event.getFeatureKey());
        } catch (Exception e) {
            log.error("Failed to publish enforcement decision: {}", e.getMessage());
        }
    }
}
