package com.modus.license.namedlicense.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.entitlement.EntitlementEvent;
import com.modus.license.namedlicense.service.NamedLicenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementEventConsumer")
class EntitlementEventConsumerTest {

    @Mock  NamedLicenseService namedLicenseService;
    @InjectMocks EntitlementEventConsumer consumer;

    static final UUID TENANT_ID      = UUID.randomUUID();
    static final UUID PLAN_ID        = UUID.randomUUID();
    static final UUID ENTITLEMENT_ID = UUID.randomUUID();

    private EntitlementEvent event(String licenseType, String eventType, Integer seatLimit) {
        EventMetadata meta = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID.toString())
                .setTimestamp(Instant.now())
                .build();

        return EntitlementEvent.newBuilder()
                .setMetadata(meta)
                .setEntitlementId(ENTITLEMENT_ID.toString())
                .setTenantId(TENANT_ID.toString())
                .setSubscriptionId(UUID.randomUUID().toString())
                .setPlanId(PLAN_ID.toString())
                .setPlanTier("PROFESSIONAL")
                .setLicenseType(licenseType)
                .setSeatLimit(seatLimit)
                .setFeatureKeys(List.of())
                .setStatus("ACTIVE")
                .setStartDate(Instant.now())
                .setEffectiveAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("ignores events for non-NAMED_USER license types")
    void ignoresNonNamedUser() {
        consumer.onEntitlementEvent(event("CONCURRENT", EventTypes.Entitlement.GRANTED, 10));

        verify(namedLicenseService, never()).processEntitlementGranted(any(), any(), any(), anyInt());
        verify(namedLicenseService, never()).processEntitlementSeatLimitChanged(any(), any(), anyInt());
    }

    @Test
    @DisplayName("GRANTED with null seatLimit skips pool creation")
    void grantedNullSeatLimit() {
        consumer.onEntitlementEvent(event("NAMED_USER", EventTypes.Entitlement.GRANTED, null));

        verify(namedLicenseService, never()).processEntitlementGranted(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("GRANTED with seatLimit calls processEntitlementGranted")
    void grantedWithSeatLimit() {
        consumer.onEntitlementEvent(event("NAMED_USER", EventTypes.Entitlement.GRANTED, 20));

        verify(namedLicenseService).processEntitlementGranted(
                TENANT_ID, PLAN_ID, ENTITLEMENT_ID, 20);
    }

    @Test
    @DisplayName("UPDATED with null seatLimit is a no-op")
    void updatedNullSeatLimit() {
        consumer.onEntitlementEvent(event("NAMED_USER", EventTypes.Entitlement.UPDATED, null));

        verify(namedLicenseService, never()).processEntitlementSeatLimitChanged(any(), any(), anyInt());
    }

    @Test
    @DisplayName("UPDATED with seatLimit calls processEntitlementSeatLimitChanged")
    void updatedWithSeatLimit() {
        consumer.onEntitlementEvent(event("NAMED_USER", EventTypes.Entitlement.UPDATED, 50));

        verify(namedLicenseService).processEntitlementSeatLimitChanged(
                TENANT_ID, ENTITLEMENT_ID, 50);
    }

    @Test
    @DisplayName("unknown event types are silently ignored")
    void unknownEventType() {
        consumer.onEntitlementEvent(event("NAMED_USER", EventTypes.Entitlement.REVOKED, null));

        verify(namedLicenseService, never()).processEntitlementGranted(any(), any(), any(), anyInt());
        verify(namedLicenseService, never()).processEntitlementSeatLimitChanged(any(), any(), anyInt());
    }
}
