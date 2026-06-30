package com.modus.license.namedlicense.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.entitlement.EntitlementEvent;
import com.modus.license.namedlicense.service.NamedLicenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reacts to EntitlementEvents for NAMED_USER license types:
 * <ul>
 *   <li>ENTITLEMENT_GRANTED — create a new seat pool for the tenant</li>
 *   <li>ENTITLEMENT_UPDATED — adjust total seats if seatLimit changed</li>
 * </ul>
 */
@Component
public class EntitlementEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntitlementEventConsumer.class);
    private static final String NAMED_USER = "NAMED_USER";

    private final NamedLicenseService namedLicenseService;

    public EntitlementEventConsumer(NamedLicenseService namedLicenseService) {
        this.namedLicenseService = namedLicenseService;
    }

    @KafkaListener(
            topics = TopicConstants.ENTITLEMENT_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEntitlementEvent(EntitlementEvent event) {
        if (!NAMED_USER.equals(event.getLicenseType())) {
            return; // only handle named-user entitlements
        }

        String eventType = event.getMetadata().getEventType();
        log.debug("Received entitlement event: type={} entitlementId={}", eventType, event.getEntitlementId());

        try {
            switch (eventType) {
                case EventTypes.Entitlement.GRANTED -> handleGranted(event);
                case EventTypes.Entitlement.UPDATED  -> handleUpdated(event);
                default -> log.debug("Ignored entitlement event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process entitlement event: type={} entitlementId={} error={}",
                    eventType, event.getEntitlementId(), e.getMessage(), e);
        }
    }

    private void handleGranted(EntitlementEvent event) {
        if (event.getSeatLimit() == null) {
            log.warn("ENTITLEMENT_GRANTED has null seatLimit, skipping pool creation: entitlementId={}",
                    event.getEntitlementId());
            return;
        }
        namedLicenseService.processEntitlementGranted(
                UUID.fromString(event.getTenantId()),
                UUID.fromString(event.getPlanId()),
                UUID.fromString(event.getEntitlementId()),
                event.getSeatLimit()
        );
        log.info("Created named-license pool for entitlement: entitlementId={} tenantId={} seatLimit={}",
                event.getEntitlementId(), event.getTenantId(), event.getSeatLimit());
    }

    private void handleUpdated(EntitlementEvent event) {
        if (event.getSeatLimit() == null) {
            log.debug("ENTITLEMENT_UPDATED has no seatLimit change, skipping: entitlementId={}", event.getEntitlementId());
            return; // no seat-limit change in this update
        }
        namedLicenseService.processEntitlementSeatLimitChanged(
                UUID.fromString(event.getTenantId()),
                UUID.fromString(event.getEntitlementId()),
                event.getSeatLimit()
        );
        log.info("Updated seat limit for named-license pool: entitlementId={} tenantId={} newSeatLimit={}",
                event.getEntitlementId(), event.getTenantId(), event.getSeatLimit());
    }
}
