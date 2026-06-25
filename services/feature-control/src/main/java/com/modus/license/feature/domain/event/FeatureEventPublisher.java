package com.modus.license.feature.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.feature.FeatureEvent;
import com.modus.license.feature.domain.entity.FeatureDefinitionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link FeatureEvent} Avro records to {@code modus.feature.events}.
 *
 * Partition key: tenantId for tenant-scoped events, featureKey for global events
 * (so all changes to the same feature land on the same partition).
 */
@Component
public class FeatureEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FeatureEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FeatureEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // -------------------------------------------------------------------------
    // Global events (tenantId = null, key = featureKey)
    // -------------------------------------------------------------------------

    public void publishGlobalEnabled(FeatureDefinitionEntity f, String previousStatus) {
        publish(buildGlobal(f, EventTypes.Feature.ENABLED, previousStatus), f.getFeatureKey());
    }

    public void publishGlobalDisabled(FeatureDefinitionEntity f, String previousStatus) {
        publish(buildGlobal(f, EventTypes.Feature.DISABLED, previousStatus), f.getFeatureKey());
    }

    public void publishGlobalUpdated(FeatureDefinitionEntity f) {
        publish(buildGlobal(f, EventTypes.Feature.UPDATED, null), f.getFeatureKey());
    }

    public void publishGlobalDeprecated(FeatureDefinitionEntity f, String previousStatus) {
        publish(buildGlobal(f, EventTypes.Feature.DEPRECATED, previousStatus), f.getFeatureKey());
    }

    // -------------------------------------------------------------------------
    // Tenant-scoped events (tenantId = tenant UUID string, key = tenantId)
    // -------------------------------------------------------------------------

    public void publishTenantEnabled(FeatureDefinitionEntity f, UUID tenantId,
                                     String previousStatus, String configJson) {
        publish(buildTenant(f, EventTypes.Feature.ENABLED, tenantId, previousStatus, configJson),
                tenantId.toString());
    }

    public void publishTenantDisabled(FeatureDefinitionEntity f, UUID tenantId, String previousStatus) {
        publish(buildTenant(f, EventTypes.Feature.DISABLED, tenantId, previousStatus, null),
                tenantId.toString());
    }

    public void publishBetaGranted(FeatureDefinitionEntity f, UUID tenantId) {
        publish(buildTenant(f, EventTypes.Feature.BETA_GRANTED, tenantId, null, null),
                tenantId.toString());
    }

    public void publishBetaRevoked(FeatureDefinitionEntity f, UUID tenantId, String previousStatus) {
        publish(buildTenant(f, EventTypes.Feature.BETA_REVOKED, tenantId, previousStatus, null),
                tenantId.toString());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private FeatureEvent buildGlobal(FeatureDefinitionEntity f, String eventType, String previousStatus) {
        return buildEvent(f, eventType, null, previousStatus, null);
    }

    private FeatureEvent buildTenant(FeatureDefinitionEntity f, String eventType, UUID tenantId,
                                      String previousStatus, String configJson) {
        return buildEvent(f, eventType, tenantId != null ? tenantId.toString() : null,
                previousStatus, configJson);
    }

    private FeatureEvent buildEvent(FeatureDefinitionEntity f, String eventType,
                                     String tenantId, String previousStatus, String configJson) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(tenantId != null ? tenantId : "global")
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return FeatureEvent.newBuilder()
                .setMetadata(metadata)
                .setFeatureId(f.getId().toString())
                .setFeatureKey(f.getFeatureKey())
                .setFeatureName(f.getName())
                .setTenantId(tenantId)
                .setStatus(f.getStatus().name())
                .setPreviousStatus(previousStatus)
                .setMinimumPlanTier(f.getMinimumPlanTier() != null ? f.getMinimumPlanTier().name() : null)
                .setConfigJson(configJson)
                .setEffectiveAt(now)
                .build();
    }

    private void publish(FeatureEvent event, String partitionKey) {
        try {
            kafkaTemplate.send(TopicConstants.FEATURE_EVENTS, partitionKey, event);
            log.debug("Published feature event: type={} featureKey={} tenantId={}",
                    event.getMetadata().getEventType(), event.getFeatureKey(), event.getTenantId());
        } catch (Exception e) {
            log.error("Failed to publish feature event: type={} featureKey={}. Error: {}",
                    event.getMetadata().getEventType(), event.getFeatureKey(), e.getMessage());
        }
    }
}
