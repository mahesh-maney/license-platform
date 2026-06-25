package com.modus.license.entitlement.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.entitlement.EntitlementEvent;
import com.modus.license.entitlement.domain.entity.EntitlementEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes {@link EntitlementEvent} Avro records to {@code modus.entitlement.events}.
 * Partition key: entitlementId.
 */
@Component
public class EntitlementEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EntitlementEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EntitlementEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishGranted(EntitlementEntity e) {
        publish(build(e, EventTypes.Entitlement.GRANTED, null));
    }

    public void publishUpdated(EntitlementEntity e) {
        publish(build(e, EventTypes.Entitlement.UPDATED, null));
    }

    public void publishExpired(EntitlementEntity e) {
        publish(build(e, EventTypes.Entitlement.EXPIRED, e.getStatus().name()));
    }

    public void publishSuspended(EntitlementEntity e, String previousStatus) {
        publish(build(e, EventTypes.Entitlement.SUSPENDED, previousStatus));
    }

    public void publishRevoked(EntitlementEntity e, String previousStatus) {
        publish(build(e, EventTypes.Entitlement.REVOKED, previousStatus));
    }

    public void publishRenewed(EntitlementEntity e) {
        publish(build(e, EventTypes.Entitlement.RENEWED, null));
    }

    private EntitlementEvent build(EntitlementEntity e, String eventType, String previousStatus) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(e.getTenantId().toString())
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return EntitlementEvent.newBuilder()
                .setMetadata(metadata)
                .setEntitlementId(e.getId().toString())
                .setTenantId(e.getTenantId().toString())
                .setSubscriptionId(e.getSubscriptionId().toString())
                .setPlanId(e.getPlanId().toString())
                .setPlanTier(e.getPlanTier().name())
                .setLicenseType(e.getLicenseType().name())
                .setSeatLimit(e.getSeatLimit())
                .setFeatureKeys(List.copyOf(e.getFeatureKeys()))
                .setStatus(e.getStatus().name())
                .setPreviousStatus(previousStatus)
                .setStartDate(e.getStartDate())
                .setEndDate(e.getEndDate())
                .setEffectiveAt(now)
                .build();
    }

    private void publish(EntitlementEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.ENTITLEMENT_EVENTS, event.getEntitlementId(), event);
            log.debug("Published entitlement event: type={} entitlementId={}",
                    event.getMetadata().getEventType(), event.getEntitlementId());
        } catch (Exception ex) {
            log.error("Failed to publish entitlement event: type={} entitlementId={}. Error: {}",
                    event.getMetadata().getEventType(), event.getEntitlementId(), ex.getMessage());
        }
    }
}
