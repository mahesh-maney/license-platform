package com.modus.license.entitlement.consumer;

import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.subscription.SubscriptionEvent;
import com.modus.license.entitlement.service.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionEventConsumer")
class SubscriptionEventConsumerTest {

    @Mock EntitlementService entitlementService;

    SubscriptionEventConsumer consumer;

    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID SUB_ID    = UUID.randomUUID();
    static final UUID PLAN_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionEventConsumer(entitlementService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SubscriptionEvent event(String eventType) {
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
        return SubscriptionEvent.newBuilder()
                .setMetadata(metadata)
                .setSubscriptionId(SUB_ID.toString())
                .setTenantId(TENANT_ID.toString())
                .setPlanId(PLAN_ID.toString())
                .setPlanTier("PROFESSIONAL")
                .setLicenseType("NAMED_USER")
                .setSeatLimit(50)
                .setBillingCycle("MONTHLY")
                .setStartDate(now)
                .setEndDate(null)
                .setPreviousPlanId(null)
                .setPreviousPlanTier(null)
                .setCancellationReason(null)
                .setEffectiveAt(now)
                .build();
    }

    // ── CREATED ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUBSCRIPTION_CREATED → processSubscriptionCreated with all fields")
    void created() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.CREATED));

        verify(entitlementService).processSubscriptionCreated(
                eq(TENANT_ID), eq(SUB_ID), eq(PLAN_ID),
                any(), any(), eq(50), any(), any());
    }

    @Test
    @DisplayName("SUBSCRIPTION_ACTIVATED → processSubscriptionCreated (same code path as CREATED)")
    void activated() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.ACTIVATED));

        verify(entitlementService).processSubscriptionCreated(
                eq(TENANT_ID), eq(SUB_ID), eq(PLAN_ID),
                any(), any(), eq(50), any(), any());
    }

    // ── UPGRADED / DOWNGRADED ─────────────────────────────────────────────────

    @Test
    @DisplayName("SUBSCRIPTION_UPGRADED → processSubscriptionPlanChanged")
    void upgraded() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.UPGRADED));

        verify(entitlementService).processSubscriptionPlanChanged(
                eq(SUB_ID), eq(PLAN_ID), any(), any(), eq(50));
    }

    @Test
    @DisplayName("SUBSCRIPTION_DOWNGRADED → processSubscriptionPlanChanged")
    void downgraded() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.DOWNGRADED));

        verify(entitlementService).processSubscriptionPlanChanged(
                eq(SUB_ID), eq(PLAN_ID), any(), any(), eq(50));
    }

    // ── Status changes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUBSCRIPTION_CANCELLED → processSubscriptionStatusChanged(REVOKED)")
    void cancelled() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.CANCELLED));

        verify(entitlementService).processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.REVOKED);
    }

    @Test
    @DisplayName("SUBSCRIPTION_SUSPENDED → processSubscriptionStatusChanged(SUSPENDED)")
    void suspended() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.SUSPENDED));

        verify(entitlementService).processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.SUSPENDED);
    }

    @Test
    @DisplayName("SUBSCRIPTION_EXPIRED → processSubscriptionStatusChanged(EXPIRED)")
    void expired() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.EXPIRED));

        verify(entitlementService).processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.EXPIRED);
    }

    @Test
    @DisplayName("SUBSCRIPTION_RENEWED → processSubscriptionStatusChanged(ACTIVE)")
    void renewed() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.RENEWED));

        verify(entitlementService).processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.ACTIVE);
    }

    // ── Unknown type ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown event type → no interaction with service")
    void unknownType() {
        consumer.onSubscriptionEvent(event("UNKNOWN_EVENT"));

        verifyNoInteractions(entitlementService);
    }
}
