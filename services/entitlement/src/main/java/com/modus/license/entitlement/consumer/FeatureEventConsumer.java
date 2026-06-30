package com.modus.license.entitlement.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.feature.FeatureEvent;
import com.modus.license.entitlement.service.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reacts to feature flag events to keep entitlement featureKey sets in sync.
 * Only processes tenant-specific events (global events with null tenantId are ignored).
 */
@Component
public class FeatureEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FeatureEventConsumer.class);

    private final EntitlementService entitlementService;

    public FeatureEventConsumer(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @KafkaListener(topics = TopicConstants.FEATURE_EVENTS)
    public void onFeatureEvent(FeatureEvent event) {
        String eventType = event.getMetadata().getEventType();
        String tenantIdStr = event.getTenantId();

        // Only process tenant-scoped feature changes
        if (tenantIdStr == null) {
            log.debug("Ignoring global feature event: type={} featureKey={}", eventType, event.getFeatureKey());
            return;
        }

        UUID tenantId = UUID.fromString(tenantIdStr);
        String featureKey = event.getFeatureKey();
        log.debug("Received feature event: type={} tenantId={} featureKey={}", eventType, tenantId, featureKey);

        switch (eventType) {
            case EventTypes.Feature.ENABLED, EventTypes.Feature.BETA_GRANTED -> {
                entitlementService.processFeatureEnabled(tenantId, featureKey);
                log.info("Added featureKey to entitlements: tenantId={} featureKey={}", tenantId, featureKey);
            }
            case EventTypes.Feature.DISABLED, EventTypes.Feature.BETA_REVOKED,
                 EventTypes.Feature.DEPRECATED -> {
                entitlementService.processFeatureDisabled(tenantId, featureKey);
                log.info("Removed featureKey from entitlements: tenantId={} featureKey={}", tenantId, featureKey);
            }
            default -> log.debug("Ignoring feature event type: {}", eventType);
        }
    }
}
