package com.modus.license.tenant.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.tenant.TenantEvent;
import com.modus.license.tenant.domain.entity.TenantEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link TenantEvent} Avro records to {@code modus.tenant.events}.
 *
 * Partition key: tenantId — all events for a tenant land on the same partition,
 * preserving order for consumers (entitlement, feature-control, notification).
 */
@Component
public class TenantEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TenantEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TenantEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(TenantEntity tenant) {
        publish(buildEvent(tenant, EventTypes.Tenant.CREATED, null));
    }

    public void publishUpdated(TenantEntity tenant) {
        publish(buildEvent(tenant, EventTypes.Tenant.UPDATED, null));
    }

    public void publishSuspended(TenantEntity tenant, String previousStatus) {
        publish(buildEvent(tenant, EventTypes.Tenant.SUSPENDED, previousStatus));
    }

    public void publishActivated(TenantEntity tenant, String previousStatus) {
        publish(buildEvent(tenant, EventTypes.Tenant.ACTIVATED, previousStatus));
    }

    public void publishDeleted(TenantEntity tenant) {
        publish(buildEvent(tenant, EventTypes.Tenant.DELETED, null));
    }

    public void publishTrialStarted(TenantEntity tenant) {
        publish(buildEvent(tenant, EventTypes.Tenant.TRIAL_STARTED, null));
    }

    private TenantEvent buildEvent(TenantEntity tenant, String eventType, String previousStatus) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(tenant.getId().toString())
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return TenantEvent.newBuilder()
                .setMetadata(metadata)
                .setTenantId(tenant.getId().toString())
                .setSlug(tenant.getSlug())
                .setName(tenant.getName())
                .setAdminEmail(tenant.getAdminEmail())
                .setStatus(tenant.getStatus().name())
                .setPlanTier(tenant.getPlanTier() != null ? tenant.getPlanTier().name() : null)
                .setRegion(tenant.getRegion())
                .setPreviousStatus(previousStatus)
                .setTrialEndsAt(tenant.getTrialEndsAt())
                .setEffectiveAt(now)
                .build();
    }

    private void publish(TenantEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.TENANT_EVENTS, event.getTenantId(), event);
            log.debug("Published tenant event: type={} tenantId={}", event.getMetadata().getEventType(), event.getTenantId());
        } catch (Exception e) {
            log.error("Failed to publish tenant event: type={} tenantId={}. Error: {}",
                    event.getMetadata().getEventType(), event.getTenantId(), e.getMessage());
        }
    }
}
