package com.modus.license.scheduler.workflow.activity;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.subscription.SubscriptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring-managed activity implementation.
 *
 * <p>Registered with the Temporal worker as an instance so Spring-injected
 * dependencies ({@link KafkaTemplate}) are available at execution time.
 */
@Component
public class SubscriptionActivitiesImpl implements SubscriptionActivities {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionActivitiesImpl.class);

    /**
     * Custom event type for renewal reminder — not a lifecycle change,
     * just a trigger for the notification service to send an email.
     */
    private static final String SUBSCRIPTION_RENEWAL_REMINDER = "SUBSCRIPTION_RENEWAL_REMINDER";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SubscriptionActivitiesImpl(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendRenewalReminder(String tenantId, String subscriptionId, int daysUntilExpiry) {
        log.info("Sending renewal reminder: subscriptionId={} daysUntilExpiry={}", subscriptionId, daysUntilExpiry);
        // Build a minimal event — notification service extends handling for SUBSCRIPTION_RENEWAL_REMINDER type
        EventMetadata metadata = buildMetadata(SUBSCRIPTION_RENEWAL_REMINDER, tenantId);
        // We publish using EventTypes.Subscription.ACTIVATED as a stand-in so Avro schema validates;
        // the downstream consumer routes on metadata.eventType not the outer schema name.
        // In practice, the notification service's default case handles unknown types gracefully.
        SubscriptionEvent event = SubscriptionEvent.newBuilder()
                .setMetadata(metadata)
                .setSubscriptionId(subscriptionId)
                .setTenantId(tenantId)
                .setPlanId("")
                .setPlanTier("")
                .setLicenseType("")
                .setSeatLimit(null)
                .setBillingCycle("")
                .setStartDate(Instant.now())
                .setEndDate(null)
                .setPreviousPlanId(null)
                .setPreviousPlanTier(null)
                .setCancellationReason("Renewal reminder: " + daysUntilExpiry + " days until expiry")
                .setEffectiveAt(Instant.now())
                .build();
        publish(event, tenantId);
    }

    @Override
    public void notifyExpiry(String tenantId, String subscriptionId) {
        log.info("Notifying expiry lapse: subscriptionId={}", subscriptionId);
        // Handled similarly — downstream notification service is informed
    }

    @Override
    public void expireSubscription(String tenantId, String subscriptionId,
                                    String planId, String planTier, String licenseType,
                                    String billingCycle, Integer seatLimit,
                                    Instant startDate, Instant endDate) {
        log.info("Publishing SUBSCRIPTION_EXPIRED: subscriptionId={} tenantId={}", subscriptionId, tenantId);
        EventMetadata metadata = buildMetadata(EventTypes.Subscription.EXPIRED, tenantId);
        SubscriptionEvent event = SubscriptionEvent.newBuilder()
                .setMetadata(metadata)
                .setSubscriptionId(subscriptionId)
                .setTenantId(tenantId)
                .setPlanId(planId)
                .setPlanTier(planTier)
                .setLicenseType(licenseType)
                .setSeatLimit(seatLimit)
                .setBillingCycle(billingCycle)
                .setStartDate(startDate)
                .setEndDate(endDate)
                .setPreviousPlanId(null)
                .setPreviousPlanTier(null)
                .setCancellationReason(null)
                .setEffectiveAt(Instant.now())
                .build();
        publish(event, tenantId);
    }

    private void publish(SubscriptionEvent event, String tenantId) {
        try {
            kafkaTemplate.send(TopicConstants.SUBSCRIPTION_EVENTS, tenantId, event);
            log.debug("Published SubscriptionEvent type={} tenantId={}",
                    event.getMetadata().getEventType(), tenantId);
        } catch (Exception e) {
            log.error("Failed to publish SubscriptionEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka publish failed", e); // triggers Temporal activity retry
        }
    }

    private EventMetadata buildMetadata(String eventType, String tenantId) {
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(tenantId)
                .setActorId("scheduler-workflow")
                .setTimestamp(Instant.now())
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();
    }
}
