package com.modus.license.enforcement.consumer;

import com.modus.license.enforcement.domain.cache.EntitlementCacheService;
import com.modus.license.enforcement.domain.model.CachedEntitlement;
import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.entitlement.EntitlementEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Warms and invalidates the enforcement engine's entitlement cache
 * by consuming {@code modus.entitlement.events}.
 */
@Component
public class EntitlementEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntitlementEventConsumer.class);

    private final EntitlementCacheService cacheService;

    public EntitlementEventConsumer(EntitlementCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @KafkaListener(
            topics = TopicConstants.ENTITLEMENT_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEntitlementEvent(EntitlementEvent event) {
        String eventType = event.getMetadata().getEventType();
        log.debug("Received entitlement event: type={} tenantId={}", eventType, event.getTenantId());

        try {
            switch (eventType) {
                case EventTypes.Entitlement.GRANTED,
                     EventTypes.Entitlement.UPDATED,
                     EventTypes.Entitlement.RENEWED  -> cacheEntitlement(event);
                case EventTypes.Entitlement.REVOKED,
                     EventTypes.Entitlement.EXPIRED,
                     EventTypes.Entitlement.SUSPENDED -> evictOrUpdateStatus(event);
                default -> log.debug("Ignored entitlement event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process entitlement event: type={} tenantId={} error={}",
                    eventType, event.getTenantId(), e.getMessage(), e);
        }
    }

    private void cacheEntitlement(EntitlementEvent event) {
        List<String> featureKeys = event.getFeatureKeys() != null
                ? event.getFeatureKeys().stream().map(Object::toString).toList()
                : List.of();

        CachedEntitlement cached = new CachedEntitlement(
                event.getEntitlementId(),
                event.getTenantId(),
                event.getLicenseType(),
                event.getStatus(),
                event.getSeatLimit(),
                featureKeys,
                event.getPlanTier(),
                Instant.now()
        );

        cacheService.put(cached)
                .subscribe(
                        unused -> {},
                        err -> log.error("Failed to cache entitlement for tenant={}: {}",
                                event.getTenantId(), err.getMessage())
                );
    }

    private void evictOrUpdateStatus(EntitlementEvent event) {
        // Update the cached status so enforcement can immediately deny access
        // without waiting for TTL expiry
        cacheService.get(event.getTenantId())
                .flatMap(existing -> {
                    CachedEntitlement updated = new CachedEntitlement(
                            existing.entitlementId(), existing.tenantId(),
                            existing.licenseType(), event.getStatus(),
                            existing.seatLimit(), existing.featureKeys(),
                            existing.planTier(), existing.effectiveAt()
                    );
                    return cacheService.put(updated);
                })
                .switchIfEmpty(cacheService.evict(event.getTenantId()))
                .subscribe(
                        unused -> {},
                        err -> log.error("Failed to update/evict entitlement cache for tenant={}: {}",
                                event.getTenantId(), err.getMessage())
                );
    }
}
