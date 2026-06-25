package com.modus.license.notification.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.subscription.SubscriptionEvent;
import com.modus.license.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@link SubscriptionEvent} records from {@code modus.subscription.events}
 * and sends lifecycle notifications to the tenant admin.
 *
 * <p>The admin email is sourced from the {@code tenant_contacts} table,
 * which is maintained by {@link TenantEventConsumer}.
 */
@Component
public class SubscriptionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final NotificationService notificationService;

    public SubscriptionEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics         = TopicConstants.SUBSCRIPTION_EVENTS,
            groupId        = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSubscriptionEvent(SubscriptionEvent event) {
        try {
            UUID tenantId = UUID.fromString(event.getTenantId());
            String eventType = event.getMetadata().getEventType();
            route(tenantId, eventType, event);
        } catch (Exception e) {
            log.error("Failed to process SubscriptionEvent type={} tenantId={}: {}",
                    event.getMetadata().getEventType(), event.getTenantId(), e.getMessage(), e);
            throw new RuntimeException("SubscriptionEvent processing failed", e);
        }
    }

    private void route(UUID tenantId, String eventType, SubscriptionEvent event) {
        switch (eventType) {
            case EventTypes.Subscription.ACTIVATED -> notificationService.notifyEmail(
                    tenantId, "SUBSCRIPTION_ACTIVATED",
                    "Your Modus subscription is now active",
                    buildBody("Your subscription is active.", event));

            case EventTypes.Subscription.RENEWED -> notificationService.notifyEmail(
                    tenantId, "SUBSCRIPTION_RENEWED",
                    "Your Modus subscription has been renewed",
                    buildBody("Your subscription has been renewed.", event));

            case EventTypes.Subscription.UPGRADED -> notificationService.notifyEmail(
                    tenantId, "SUBSCRIPTION_UPGRADED",
                    "Your Modus subscription has been upgraded",
                    buildBody("Your subscription has been upgraded to " + event.getPlanTier() + ".", event));

            case EventTypes.Subscription.DOWNGRADED -> notificationService.notifyEmail(
                    tenantId, "SUBSCRIPTION_DOWNGRADED",
                    "Your Modus subscription has been downgraded",
                    buildBody("Your subscription has been downgraded to " + event.getPlanTier() + ".", event));

            case EventTypes.Subscription.CANCELLED -> notificationService.notifyEmail(
                    tenantId, "SUBSCRIPTION_CANCELLED",
                    "Your Modus subscription has been cancelled",
                    buildBody("Your subscription has been cancelled. Reason: "
                            + (event.getCancellationReason() != null ? event.getCancellationReason() : "N/A")
                            + ".", event));

            case EventTypes.Subscription.EXPIRED -> notificationService.notifyEmail(
                    tenantId, "SUBSCRIPTION_EXPIRED",
                    "Your Modus subscription has expired",
                    buildBody("Your subscription has expired. Please renew to continue.", event));

            case EventTypes.Subscription.SUSPENDED -> notificationService.notifyEmail(
                    tenantId, "SUBSCRIPTION_SUSPENDED",
                    "Your Modus subscription has been suspended",
                    buildBody("Your subscription has been suspended. Please contact support.", event));

            default -> log.debug("No notification configured for SubscriptionEvent type={}", eventType);
        }
    }

    private String buildBody(String headline, SubscriptionEvent event) {
        return headline + "\n\n"
                + "Plan: " + event.getPlanTier() + "\n"
                + "License type: " + event.getLicenseType() + "\n"
                + "Billing cycle: " + event.getBillingCycle() + "\n"
                + "Effective: " + event.getEffectiveAt() + "\n\n"
                + "Log in to the Modus portal for more details.";
    }
}
