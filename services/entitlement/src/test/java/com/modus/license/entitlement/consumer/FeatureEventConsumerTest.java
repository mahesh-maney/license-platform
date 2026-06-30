package com.modus.license.entitlement.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.feature.FeatureEvent;
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
@DisplayName("FeatureEventConsumer (entitlement)")
class FeatureEventConsumerTest {

    @Mock EntitlementService entitlementService;

    FeatureEventConsumer consumer;

    static final UUID TENANT_ID   = UUID.randomUUID();
    static final UUID FEATURE_ID  = UUID.randomUUID();
    static final String FEATURE_KEY = "EXPORT_PDF";

    @BeforeEach
    void setUp() {
        consumer = new FeatureEventConsumer(entitlementService);
    }

    private FeatureEvent event(String eventType, String tenantId) {
        Instant now = Instant.now();
        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(tenantId)
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();
        return FeatureEvent.newBuilder()
                .setMetadata(metadata)
                .setFeatureId(FEATURE_ID.toString())
                .setFeatureKey(FEATURE_KEY)
                .setFeatureName("Export PDF")
                .setTenantId(tenantId)
                .setStatus("ENABLED")
                .setPreviousStatus(null)
                .setMinimumPlanTier(null)
                .setConfigJson(null)
                .setEffectiveAt(now)
                .build();
    }

    @Test
    @DisplayName("FEATURE_ENABLED (tenant-scoped) → processFeatureEnabled")
    void enabled() {
        consumer.onFeatureEvent(event(EventTypes.Feature.ENABLED, TENANT_ID.toString()));

        verify(entitlementService).processFeatureEnabled(eq(TENANT_ID), eq(FEATURE_KEY));
    }

    @Test
    @DisplayName("FEATURE_BETA_GRANTED (tenant-scoped) → processFeatureEnabled")
    void betaGranted() {
        consumer.onFeatureEvent(event(EventTypes.Feature.BETA_GRANTED, TENANT_ID.toString()));

        verify(entitlementService).processFeatureEnabled(eq(TENANT_ID), eq(FEATURE_KEY));
    }

    @Test
    @DisplayName("FEATURE_DISABLED (tenant-scoped) → processFeatureDisabled")
    void disabled() {
        consumer.onFeatureEvent(event(EventTypes.Feature.DISABLED, TENANT_ID.toString()));

        verify(entitlementService).processFeatureDisabled(eq(TENANT_ID), eq(FEATURE_KEY));
    }

    @Test
    @DisplayName("FEATURE_BETA_REVOKED (tenant-scoped) → processFeatureDisabled")
    void betaRevoked() {
        consumer.onFeatureEvent(event(EventTypes.Feature.BETA_REVOKED, TENANT_ID.toString()));

        verify(entitlementService).processFeatureDisabled(eq(TENANT_ID), eq(FEATURE_KEY));
    }

    @Test
    @DisplayName("FEATURE_DEPRECATED (tenant-scoped) → processFeatureDisabled")
    void deprecated() {
        consumer.onFeatureEvent(event(EventTypes.Feature.DEPRECATED, TENANT_ID.toString()));

        verify(entitlementService).processFeatureDisabled(eq(TENANT_ID), eq(FEATURE_KEY));
    }

    @Test
    @DisplayName("global event (null tenantId) → no interaction with service")
    void globalEventIgnored() {
        consumer.onFeatureEvent(event(EventTypes.Feature.ENABLED, null));

        verifyNoInteractions(entitlementService);
    }

    @Test
    @DisplayName("unknown event type (tenant-scoped) → no interaction with service")
    void unknownType() {
        consumer.onFeatureEvent(event("UNKNOWN_EVENT", TENANT_ID.toString()));

        verifyNoInteractions(entitlementService);
    }
}
