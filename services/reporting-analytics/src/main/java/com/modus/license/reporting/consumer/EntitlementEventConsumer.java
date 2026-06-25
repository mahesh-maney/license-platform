package com.modus.license.reporting.consumer;

import com.modus.license.events.TopicConstants;
import com.modus.license.events.entitlement.EntitlementEvent;
import com.modus.license.reporting.domain.entity.EntitlementSnapshotEntity;
import com.modus.license.reporting.domain.repository.EntitlementSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consumes {@link EntitlementEvent} records from {@code modus.entitlement.events} and
 * maintains a current-state snapshot of each entitlement for reporting queries.
 *
 * Every event type (GRANTED, UPDATED, EXPIRED, SUSPENDED, REVOKED, RENEWED) is
 * an upsert: the snapshot row is created if new, updated if existing.
 */
@Component
public class EntitlementEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntitlementEventConsumer.class);

    private final EntitlementSnapshotRepository repository;

    public EntitlementEventConsumer(EntitlementSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @KafkaListener(
            topics           = TopicConstants.ENTITLEMENT_EVENTS,
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEntitlementEvent(EntitlementEvent event) {
        try {
            UUID entitlementId = UUID.fromString(event.getEntitlementId());

            EntitlementSnapshotEntity snapshot = repository.findById(entitlementId)
                    .orElse(new EntitlementSnapshotEntity());

            snapshot.setEntitlementId(entitlementId);
            snapshot.setTenantId(UUID.fromString(event.getTenantId()));
            snapshot.setSubscriptionId(UUID.fromString(event.getSubscriptionId()));
            snapshot.setPlanId(UUID.fromString(event.getPlanId()));
            snapshot.setPlanTier(event.getPlanTier());
            snapshot.setLicenseType(event.getLicenseType());
            snapshot.setSeatLimit(event.getSeatLimit());
            snapshot.setFeatureKeys(serializeFeatureKeys(event.getFeatureKeys()));
            snapshot.setStatus(event.getStatus());
            snapshot.setStartDate(event.getStartDate());
            snapshot.setEndDate(event.getEndDate());
            snapshot.setRecordedAt(event.getEffectiveAt());

            repository.save(snapshot);
            log.debug("Upserted entitlement snapshot: entitlementId={} tenantId={} status={}",
                    entitlementId, event.getTenantId(), event.getStatus());

        } catch (Exception e) {
            log.error("Failed to process EntitlementEvent entitlementId={}: {}",
                    event.getEntitlementId(), e.getMessage(), e);
            throw new RuntimeException("EntitlementEvent processing failed", e);
        }
    }

    private String serializeFeatureKeys(List<CharSequence> featureKeys) {
        if (featureKeys == null || featureKeys.isEmpty()) return "";
        return String.join(",", featureKeys.stream().map(CharSequence::toString).toList());
    }
}
