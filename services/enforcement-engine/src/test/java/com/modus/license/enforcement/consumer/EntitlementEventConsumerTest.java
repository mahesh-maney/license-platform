package com.modus.license.enforcement.consumer;

import com.modus.license.enforcement.domain.cache.EntitlementCacheService;
import com.modus.license.enforcement.domain.model.CachedEntitlement;
import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.entitlement.EntitlementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementEventConsumer")
class EntitlementEventConsumerTest {

    @Mock EntitlementCacheService cacheService;

    EntitlementEventConsumer consumer;

    static final String TENANT_ID      = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    static final String ENTITLEMENT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        consumer = new EntitlementEventConsumer(cacheService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EntitlementEvent event(String eventType, String status) {
        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID)
                .setActorId(null)
                .setTimestamp(Instant.now())
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return EntitlementEvent.newBuilder()
                .setMetadata(metadata)
                .setEntitlementId(ENTITLEMENT_ID)
                .setTenantId(TENANT_ID)
                .setSubscriptionId(UUID.randomUUID().toString())
                .setPlanId(UUID.randomUUID().toString())
                .setPlanTier("PROFESSIONAL")
                .setLicenseType("NAMED_USER")
                .setSeatLimit(null)
                .setFeatureKeys(List.of("feature.export", "feature.read"))
                .setStatus(status)
                .setPreviousStatus(null)
                .setStartDate(Instant.now())
                .setEndDate(null)
                .setEffectiveAt(Instant.now())
                .build();
    }

    private CachedEntitlement cachedActive() {
        return new CachedEntitlement(
                ENTITLEMENT_ID, TENANT_ID, "NAMED_USER", "ACTIVE",
                null, List.of("feature.export", "feature.read"), "PROFESSIONAL", Instant.now());
    }

    // ── GRANTED / UPDATED / RENEWED → cache ───────────────────────────────────

    @Test
    @DisplayName("ENTITLEMENT_GRANTED warms the cache")
    void grantedCachesEntitlement() {
        when(cacheService.put(any())).thenReturn(Mono.empty());

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.GRANTED, "ACTIVE"));

        ArgumentCaptor<CachedEntitlement> captor = ArgumentCaptor.forClass(CachedEntitlement.class);
        verify(cacheService).put(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().status()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().featureKeys()).contains("feature.export");
    }

    @Test
    @DisplayName("ENTITLEMENT_UPDATED refreshes the cache")
    void updatedRefreshesCache() {
        when(cacheService.put(any())).thenReturn(Mono.empty());

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.UPDATED, "ACTIVE"));

        verify(cacheService).put(any());
    }

    @Test
    @DisplayName("ENTITLEMENT_RENEWED refreshes the cache")
    void renewedRefreshesCache() {
        when(cacheService.put(any())).thenReturn(Mono.empty());

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.RENEWED, "ACTIVE"));

        verify(cacheService).put(any());
    }

    // ── REVOKED / EXPIRED / SUSPENDED → update status or evict ───────────────

    @Test
    @DisplayName("ENTITLEMENT_REVOKED updates cached status when entry exists")
    void revokedUpdatesStatusInCache() {
        when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(cachedActive()));
        when(cacheService.put(any())).thenReturn(Mono.empty());

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.REVOKED, "REVOKED"));

        ArgumentCaptor<CachedEntitlement> captor = ArgumentCaptor.forClass(CachedEntitlement.class);
        verify(cacheService).put(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REVOKED");
        verify(cacheService, never()).evict(any());
    }

    @Test
    @DisplayName("ENTITLEMENT_EXPIRED evicts cache when entry does not exist")
    void expiredEvictsWhenNotCached() {
        when(cacheService.get(TENANT_ID)).thenReturn(Mono.empty());
        when(cacheService.evict(TENANT_ID)).thenReturn(Mono.empty());

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.EXPIRED, "EXPIRED"));

        verify(cacheService).evict(eq(TENANT_ID));
        verify(cacheService, never()).put(any());
    }

    @Test
    @DisplayName("ENTITLEMENT_SUSPENDED updates cached status when entry exists")
    void suspendedUpdatesStatus() {
        when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(cachedActive()));
        when(cacheService.put(any())).thenReturn(Mono.empty());

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.SUSPENDED, "SUSPENDED"));

        ArgumentCaptor<CachedEntitlement> captor = ArgumentCaptor.forClass(CachedEntitlement.class);
        verify(cacheService).put(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("SUSPENDED");
    }

    // ── Unknown event type → ignore ───────────────────────────────────────────

    @Test
    @DisplayName("Unknown event type is silently ignored")
    void unknownEventTypeIgnored() {
        consumer.onEntitlementEvent(event("UNKNOWN_TYPE", "ACTIVE"));

        verify(cacheService, never()).get(any());
        verify(cacheService, never()).put(any());
        verify(cacheService, never()).evict(any());
    }
}
