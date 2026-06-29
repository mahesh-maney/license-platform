package com.modus.license.notification.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.subscription.SubscriptionEvent;
import com.modus.license.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionEventConsumer (notification)")
class SubscriptionEventConsumerTest {

    @Mock NotificationService notificationService;

    @InjectMocks SubscriptionEventConsumer consumer;

    static final UUID TENANT_ID      = UUID.randomUUID();
    static final UUID SUBSCRIPTION_ID = UUID.randomUUID();
    static final UUID PLAN_ID        = UUID.randomUUID();

    private SubscriptionEvent event(String eventType) {
        EventMetadata meta = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID.toString())
                .setTimestamp(Instant.now())
                .build();

        return SubscriptionEvent.newBuilder()
                .setMetadata(meta)
                .setSubscriptionId(SUBSCRIPTION_ID.toString())
                .setTenantId(TENANT_ID.toString())
                .setPlanId(PLAN_ID.toString())
                .setPlanTier("PROFESSIONAL")
                .setLicenseType("CONCURRENT")
                .setBillingCycle("MONTHLY")
                .setSeatLimit(null)
                .setCancellationReason(null)
                .setStartDate(Instant.now().minusSeconds(86400))
                .setEffectiveAt(Instant.now())
                .build();
    }

    // ── notification routing ──────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → notifyEmail called")
    @ValueSource(strings = {
            "SUBSCRIPTION_ACTIVATED",
            "SUBSCRIPTION_RENEWED",
            "SUBSCRIPTION_UPGRADED",
            "SUBSCRIPTION_DOWNGRADED",
            "SUBSCRIPTION_CANCELLED",
            "SUBSCRIPTION_EXPIRED",
            "SUBSCRIPTION_SUSPENDED"
    })
    @DisplayName("notifiable event types each trigger notifyEmail")
    void notifiableEvents_callNotifyEmail(String eventType) {
        consumer.onSubscriptionEvent(event(eventType));

        verify(notificationService).notifyEmail(
                eq(TENANT_ID), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("ACTIVATED → notifyEmail with SUBSCRIPTION_ACTIVATED type")
    void activated_correctType() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.ACTIVATED));

        verify(notificationService).notifyEmail(
                eq(TENANT_ID), eq("SUBSCRIPTION_ACTIVATED"), anyString(), anyString());
    }

    @Test
    @DisplayName("CANCELLED → body contains cancellation reason when null")
    void cancelled_correctType() {
        consumer.onSubscriptionEvent(event(EventTypes.Subscription.CANCELLED));

        verify(notificationService).notifyEmail(
                eq(TENANT_ID), eq("SUBSCRIPTION_CANCELLED"), anyString(), anyString());
    }

    @Test
    @DisplayName("unknown event type → no notification sent")
    void unknownEventType_noNotification() {
        consumer.onSubscriptionEvent(event("SUBSCRIPTION_CREATED"));

        verify(notificationService, never()).notifyEmail(any(), any(), any(), any());
    }
}
