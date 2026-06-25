package com.modus.license.subscription.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.subscription.SubscriptionEvent;
import com.modus.license.subscription.domain.entity.SubscriptionEntity;
import com.modus.license.subscription.domain.entity.SubscriptionPlanEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link SubscriptionEvent} Avro records to {@code modus.subscription.events}.
 *
 * Partition key: subscriptionId — all lifecycle events for a subscription are ordered.
 */
@Component
public class SubscriptionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SubscriptionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(SubscriptionEntity sub) {
        publish(buildEvent(sub, EventTypes.Subscription.CREATED, null, null, null));
    }

    public void publishActivated(SubscriptionEntity sub) {
        publish(buildEvent(sub, EventTypes.Subscription.ACTIVATED, null, null, null));
    }

    public void publishUpgraded(SubscriptionEntity sub, SubscriptionPlanEntity previousPlan) {
        publish(buildEvent(sub, EventTypes.Subscription.UPGRADED,
                previousPlan.getId().toString(), previousPlan.getTier().name(), null));
    }

    public void publishDowngraded(SubscriptionEntity sub, SubscriptionPlanEntity previousPlan) {
        publish(buildEvent(sub, EventTypes.Subscription.DOWNGRADED,
                previousPlan.getId().toString(), previousPlan.getTier().name(), null));
    }

    public void publishCancelled(SubscriptionEntity sub) {
        publish(buildEvent(sub, EventTypes.Subscription.CANCELLED, null, null, sub.getCancellationReason()));
    }

    public void publishExpired(SubscriptionEntity sub) {
        publish(buildEvent(sub, EventTypes.Subscription.EXPIRED, null, null, null));
    }

    public void publishRenewed(SubscriptionEntity sub) {
        publish(buildEvent(sub, EventTypes.Subscription.RENEWED, null, null, null));
    }

    public void publishSuspended(SubscriptionEntity sub) {
        publish(buildEvent(sub, EventTypes.Subscription.SUSPENDED, null, null, null));
    }

    private SubscriptionEvent buildEvent(SubscriptionEntity sub, String eventType,
                                          String previousPlanId, String previousPlanTier,
                                          String cancellationReason) {
        Instant now = Instant.now();
        SubscriptionPlanEntity plan = sub.getPlan();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(sub.getTenantId().toString())
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return SubscriptionEvent.newBuilder()
                .setMetadata(metadata)
                .setSubscriptionId(sub.getId().toString())
                .setTenantId(sub.getTenantId().toString())
                .setPlanId(plan.getId().toString())
                .setPlanTier(plan.getTier().name())
                .setLicenseType(plan.getLicenseType().name())
                .setSeatLimit(sub.effectiveSeatLimit())
                .setBillingCycle(sub.getBillingCycle().name())
                .setStartDate(sub.getStartDate())
                .setEndDate(sub.getEndDate())
                .setPreviousPlanId(previousPlanId)
                .setPreviousPlanTier(previousPlanTier)
                .setCancellationReason(cancellationReason)
                .setEffectiveAt(now)
                .build();
    }

    private void publish(SubscriptionEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.SUBSCRIPTION_EVENTS, event.getSubscriptionId(), event);
            log.debug("Published subscription event: type={} subscriptionId={}",
                    event.getMetadata().getEventType(), event.getSubscriptionId());
        } catch (Exception e) {
            log.error("Failed to publish subscription event: type={} subscriptionId={}. Error: {}",
                    event.getMetadata().getEventType(), event.getSubscriptionId(), e.getMessage());
        }
    }
}
