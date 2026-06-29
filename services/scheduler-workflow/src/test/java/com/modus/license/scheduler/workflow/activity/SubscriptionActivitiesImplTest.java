package com.modus.license.scheduler.workflow.activity;

import com.modus.license.events.TopicConstants;
import com.modus.license.events.subscription.SubscriptionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionActivitiesImpl")
class SubscriptionActivitiesImplTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks SubscriptionActivitiesImpl activities;

    static final String TENANT_ID       = UUID.randomUUID().toString();
    static final String SUBSCRIPTION_ID = UUID.randomUUID().toString();

    // ── sendRenewalReminder ───────────────────────────────────────────────────

    @Test
    @DisplayName("sendRenewalReminder → publishes SubscriptionEvent with tenantId as key")
    void sendRenewalReminder_publishesEvent() {
        activities.sendRenewalReminder(TENANT_ID, SUBSCRIPTION_ID, 30);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TopicConstants.SUBSCRIPTION_EVENTS), eq(TENANT_ID), captor.capture());

        SubscriptionEvent event = (SubscriptionEvent) captor.getValue();
        assertThat(event.getSubscriptionId().toString()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(event.getTenantId().toString()).isEqualTo(TENANT_ID);
        assertThat(event.getMetadata().getEventType().toString()).isEqualTo("SUBSCRIPTION_RENEWAL_REMINDER");
    }

    @Test
    @DisplayName("sendRenewalReminder → Kafka failure propagates as RuntimeException")
    void sendRenewalReminder_kafkaFails_throwsRuntime() {
        doThrow(new RuntimeException("broker unavailable"))
                .when(kafkaTemplate).send(any(String.class), any(String.class), any());

        assertThatThrownBy(() -> activities.sendRenewalReminder(TENANT_ID, SUBSCRIPTION_ID, 7))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka publish failed");
    }

    // ── expireSubscription ────────────────────────────────────────────────────

    @Test
    @DisplayName("expireSubscription → publishes SUBSCRIPTION_EXPIRED event")
    void expireSubscription_publishesExpiredEvent() {
        Instant start = Instant.now().minusSeconds(86_400 * 365);
        Instant end   = Instant.now().minusSeconds(3600);

        activities.expireSubscription(
                TENANT_ID, SUBSCRIPTION_ID,
                "plan-1", "ENTERPRISE", "NAMED_USER",
                "ANNUAL", 50,
                start, end);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TopicConstants.SUBSCRIPTION_EVENTS), eq(TENANT_ID), captor.capture());

        SubscriptionEvent event = (SubscriptionEvent) captor.getValue();
        assertThat(event.getMetadata().getEventType().toString()).isEqualTo("SUBSCRIPTION_EXPIRED");
        assertThat(event.getPlanTier().toString()).isEqualTo("ENTERPRISE");
    }

    // ── notifyExpiry ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyExpiry → completes without exception (no Kafka call)")
    void notifyExpiry_noException() {
        activities.notifyExpiry(TENANT_ID, SUBSCRIPTION_ID);
        // no verify — method is intentionally a no-op stub; just assert it doesn't throw
    }
}
