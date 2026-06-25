package com.modus.license.usage.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.enforcement.EnforcementDecision;
import com.modus.license.usage.domain.event.UsageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Converts enforcement decisions into usage records.
 *
 * Every ACCESS_ALLOWED decision counts as a metered API call for the feature.
 */
@Component
public class EnforcementEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EnforcementEventConsumer.class);

    private static final String METRIC_API_CALLS = "API_CALLS";
    private static final String UNIT_CALLS       = "CALLS";

    private final UsageEventPublisher publisher;

    public EnforcementEventConsumer(UsageEventPublisher publisher) {
        this.publisher = publisher;
    }

    @KafkaListener(
            topics = TopicConstants.ENFORCEMENT_DECISIONS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEnforcementDecision(EnforcementDecision decision) {
        String eventType = decision.getMetadata().getEventType();

        // Only record usage for allowed decisions — denied ones don't consume resources
        if (!EventTypes.Enforcement.ALLOWED.equals(eventType)) {
            return;
        }

        try {
            publisher.publishRecorded(
                    decision.getTenantId(),
                    decision.getUserId(),
                    decision.getFeatureKey(),
                    METRIC_API_CALLS,
                    1.0,
                    UNIT_CALLS
            );
        } catch (Exception e) {
            log.error("Failed to record usage for enforcement decision: tenantId={} featureKey={} error={}",
                    decision.getTenantId(), decision.getFeatureKey(), e.getMessage());
        }
    }
}
