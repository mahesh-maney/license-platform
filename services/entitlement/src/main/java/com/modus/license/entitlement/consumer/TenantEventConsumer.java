package com.modus.license.entitlement.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.tenant.TenantEvent;
import com.modus.license.entitlement.service.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reacts to tenant lifecycle events to cascade suspension and reactivation
 * to all of the tenant's entitlements.
 */
@Component
public class TenantEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TenantEventConsumer.class);

    private final EntitlementService entitlementService;

    public TenantEventConsumer(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @KafkaListener(topics = TopicConstants.TENANT_EVENTS)
    public void onTenantEvent(TenantEvent event) {
        String eventType = event.getMetadata().getEventType();
        UUID tenantId = UUID.fromString(event.getTenantId());
        log.debug("Received tenant event: type={} tenantId={}", eventType, tenantId);

        switch (eventType) {
            case EventTypes.Tenant.SUSPENDED -> entitlementService.processTenantSuspended(tenantId);
            case EventTypes.Tenant.ACTIVATED -> entitlementService.processTenantActivated(tenantId);
            default -> log.debug("Ignoring tenant event type: {}", eventType);
        }
    }
}
