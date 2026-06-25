package com.modus.license.reporting.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.subscription.SubscriptionEvent;
import com.modus.license.reporting.domain.entity.SubscriptionSnapshotEntity;
import com.modus.license.reporting.domain.repository.SubscriptionSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes {@link SubscriptionEvent} records from {@code modus.subscription.events} and
 * maintains a current-state snapshot of each subscription for reporting queries.
 *
 * Status is derived from the event type (no explicit status field in the schema).
 */
@Component
public class SubscriptionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final SubscriptionSnapshotRepository repository;

    public SubscriptionEventConsumer(SubscriptionSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @KafkaListener(
            topics           = TopicConstants.SUBSCRIPTION_EVENTS,
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSubscriptionEvent(SubscriptionEvent event) {
        try {
            UUID subscriptionId = UUID.fromString(event.getSubscriptionId());
            String status = deriveStatus(event.getMetadata().getEventType());

            SubscriptionSnapshotEntity snapshot = repository.findById(subscriptionId)
                    .orElse(new SubscriptionSnapshotEntity());

            snapshot.setSubscriptionId(subscriptionId);
            snapshot.setTenantId(UUID.fromString(event.getTenantId()));
            snapshot.setPlanId(UUID.fromString(event.getPlanId()));
            snapshot.setPlanTier(event.getPlanTier());
            snapshot.setLicenseType(event.getLicenseType());
            snapshot.setBillingCycle(event.getBillingCycle());
            snapshot.setSeatLimit(event.getSeatLimit());
            snapshot.setStatus(status);
            snapshot.setStartDate(event.getStartDate());
            snapshot.setEndDate(event.getEndDate());
            snapshot.setRecordedAt(event.getEffectiveAt());

            repository.save(snapshot);
            log.debug("Upserted subscription snapshot: subscriptionId={} tenantId={} status={}",
                    subscriptionId, event.getTenantId(), status);

        } catch (Exception e) {
            log.error("Failed to process SubscriptionEvent subscriptionId={}: {}",
                    event.getSubscriptionId(), e.getMessage(), e);
            throw new RuntimeException("SubscriptionEvent processing failed", e);
        }
    }

    private String deriveStatus(String eventType) {
        return switch (eventType) {
            case EventTypes.Subscription.CREATED    -> "PENDING";
            case EventTypes.Subscription.ACTIVATED,
                 EventTypes.Subscription.RENEWED,
                 EventTypes.Subscription.UPGRADED,
                 EventTypes.Subscription.DOWNGRADED -> "ACTIVE";
            case EventTypes.Subscription.SUSPENDED  -> "SUSPENDED";
            case EventTypes.Subscription.CANCELLED  -> "CANCELLED";
            case EventTypes.Subscription.EXPIRED    -> "EXPIRED";
            default                                  -> "UNKNOWN";
        };
    }
}
