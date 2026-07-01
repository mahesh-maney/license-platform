package com.modus.license.entitlement.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.tenant.TenantEvent;
import com.modus.license.entitlement.service.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantEventConsumer (entitlement)")
class TenantEventConsumerTest {

    @Mock EntitlementService entitlementService;

    TenantEventConsumer consumer;

    static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TenantEventConsumer(entitlementService);
    }

    private TenantEvent event(String eventType) {
        Instant now = Instant.now();
        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID.toString())
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();
        return TenantEvent.newBuilder()
                .setMetadata(metadata)
                .setTenantId(TENANT_ID.toString())
                .setSlug("acme-corp")
                .setName("Acme Corp")
                .setAdminEmail("admin@acme.com")
                .setStatus("ACTIVE")
                .setPlanTier(null)
                .setRegion(null)
                .setPreviousStatus(null)
                .setTrialEndsAt(null)
                .setEffectiveAt(now)
                .build();
    }

    @Test
    @DisplayName("TENANT_SUSPENDED → processTenantSuspended")
    void suspended() {
        consumer.onTenantEvent(event(EventTypes.Tenant.SUSPENDED));

        verify(entitlementService).processTenantSuspended(eq(TENANT_ID));
    }

    @Test
    @DisplayName("TENANT_ACTIVATED → processTenantActivated")
    void activated() {
        consumer.onTenantEvent(event(EventTypes.Tenant.ACTIVATED));

        verify(entitlementService).processTenantActivated(eq(TENANT_ID));
    }

    @Test
    @DisplayName("unknown event type → no interaction with service")
    void unknownType() {
        consumer.onTenantEvent(event("TENANT_CREATED"));

        verifyNoInteractions(entitlementService);
    }
}
