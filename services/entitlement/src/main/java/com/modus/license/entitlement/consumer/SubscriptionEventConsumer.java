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
                log.info("Granted entitlement for subscription: subscriptionId={} tenantId={} planTier={} licenseType={}",
                        event.getSubscriptionId(), event.getTenantId(), event.getPlanTier(), event.getLicenseType());
            }
            case EventTypes.Subscription.UPGRADED, EventTypes.Subscription.DOWNGRADED -> {
                entitlementService.processSubscriptionPlanChanged(
                        UUID.fromString(event.getSubscriptionId()),
                        UUID.fromString(event.getPlanId()),
                        PlanTier.valueOf(event.getPlanTier()),
                        LicenseType.valueOf(event.getLicenseType()),
                        event.getSeatLimit()
                );
                log.info("Updated entitlement plan: subscriptionId={} newPlanTier={} newLicenseType={} newSeatLimit={}",
                        event.getSubscriptionId(), event.getPlanTier(), event.getLicenseType(), event.getSeatLimit());
            }
            case EventTypes.Subscription.CANCELLED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.REVOKED);
                log.info("Revoked entitlement for cancelled subscription: subscriptionId={} tenantId={}",
                        event.getSubscriptionId(), event.getTenantId());
            }
            case EventTypes.Subscription.SUSPENDED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.SUSPENDED);
                log.info("Suspended entitlement for suspended subscription: subscriptionId={} tenantId={}",
                        event.getSubscriptionId(), event.getTenantId());
            }
            case EventTypes.Subscription.EXPIRED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.EXPIRED);
                log.info("Expired entitlement for expired subscription: subscriptionId={} tenantId={}",
                        event.getSubscriptionId(), event.getTenantId());
            }
            case EventTypes.Subscription.RENEWED -> {
                entitlementService.processSubscriptionStatusChanged(
                        UUID.fromString(event.getSubscriptionId()), EntitlementStatus.ACTIVE);
                log.info("Reactivated entitlement for renewed subscription: subscriptionId={} tenantId={}",
                        event.getSubscriptionId(), event.getTenantId());
            }
            default -> log.debug("Ignoring subscription event type: {}", eventType);
        }
    }
}
