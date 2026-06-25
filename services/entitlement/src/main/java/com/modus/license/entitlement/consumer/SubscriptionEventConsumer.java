package com.modus.license.entitlement.consumer;

import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.subscription.SubscriptionEvent;
import com.modus.license.entitlement.service.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reacts to subscription lifecycle events to automatically grant, update,
 * suspend, revoke, expire, and renew entitlements.
 */
@Component
public class SubscriptionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final EntitlementService entitlementService;

    public SubscriptionEventConsumer(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @KafkaListener(topics = TopicConstants.SUBSCRIPTION_EVENTS)
    public void onSubscriptionEvent(SubscriptionEvent event) {
        String eventType = event.getMetadata().getEventType();
        log.debug("Received subscription event: type={} subscriptionId={}", eventType, event.getSubscriptionId());

        switch (eventType) {
            case EventTypes.Subscription.CREATED, EventTypes.Subscription.ACTIVATED -> {
                entitlementService.processSubscriptionCreated(
                        UUID.fromString(event.getTenantId()),
                        UUID.fromString(event.getSubscriptionId()),
                        UUID.fromString(event.getPlanId()),
                        PlanTier.valueOf(event.getPlanTier()),
                        LicenseType.valueOf(event.getLicenseType()),
                        event.getSeatLimit(),
                        event.getStartDate(),
                        event.getEndDate()
                );
            }
            case EventTypes.Subscription.UPGRADED, EventTypes.Subscription.DOWNGRADED -> {
                entitlementService.processSubscriptionPlanChanged(
                        UUID.fromString(event.getSubscriptionId()),
                        UUID.fromString(event.getPlanId()),
                        PlanTier.valueOf(event.getPlanTier()),
                        LicenseType.valueOf(event.getLicenseType()),
                        event.getSeatLimit()
                );
            }
            case EventTypes.Subscription.CANCELLED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.REVOKED);
            }
            case EventTypes.Subscription.SUSPENDED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.SUSPENDED);
            }
            case EventTypes.Subscription.EXPIRED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.EXPIRED);
            }
            case EventTypes.Subscription.RENEWED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.ACTIVE);
            }
            default -> log.debug("Ignoring subscription event type: {}", eventType);
        }
    }
}
