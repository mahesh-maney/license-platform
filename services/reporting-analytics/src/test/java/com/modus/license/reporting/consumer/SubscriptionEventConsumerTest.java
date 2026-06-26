package com.modus.license.reporting.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.subscription.SubscriptionEvent;
import com.modus.license.reporting.domain.entity.SubscriptionSnapshotEntity;
import com.modus.license.reporting.domain.repository.SubscriptionSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionEventConsumer (reporting)")
class SubscriptionEventConsumerTest {

    @Mock  SubscriptionSnapshotRepository repository;
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
                .setStartDate(Instant.now().minusSeconds(86400))
                .setEffectiveAt(Instant.now())
                .build();
    }

    // ── deriveStatus mapping ──────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → status={1}")
    @CsvSource({
            "SUBSCRIPTION_CREATED,   PENDING",
            "SUBSCRIPTION_ACTIVATED, ACTIVE",
            "SUBSCRIPTION_RENEWED,   ACTIVE",
            "SUBSCRIPTION_UPGRADED,  ACTIVE",
            "SUBSCRIPTION_DOWNGRADED,ACTIVE",
            "SUBSCRIPTION_SUSPENDED, SUSPENDED",
            "SUBSCRIPTION_CANCELLED, CANCELLED",
            "SUBSCRIPTION_EXPIRED,   EXPIRED",
            "UNKNOWN_EVENT_TYPE,     UNKNOWN"
    })
    @DisplayName("event type derives correct snapshot status")
    void deriveStatus(String eventType, String expectedStatus) {
        when(repository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onSubscriptionEvent(event(eventType.trim()));

        ArgumentCaptor<SubscriptionSnapshotEntity> cap =
                ArgumentCaptor.forClass(SubscriptionSnapshotEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(expectedStatus.trim());
    }

    // ── upsert behaviour ──────────────────────────────────────────────────────

    @Test
    @DisplayName("CREATED (new) → creates snapshot with correct field values")
    void created_newSnapshot() {
        when(repository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onSubscriptionEvent(event(EventTypes.Subscription.CREATED));

        ArgumentCaptor<SubscriptionSnapshotEntity> cap =
                ArgumentCaptor.forClass(SubscriptionSnapshotEntity.class);
        verify(repository).save(cap.capture());
        SubscriptionSnapshotEntity saved = cap.getValue();
        assertThat(saved.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(saved.getPlanTier()).isEqualTo("PROFESSIONAL");
        assertThat(saved.getBillingCycle()).isEqualTo("MONTHLY");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("ACTIVATED (existing) → updates snapshot in-place")
    void activated_updatesExistingSnapshot() {
        SubscriptionSnapshotEntity existing = new SubscriptionSnapshotEntity();
        existing.setSubscriptionId(SUBSCRIPTION_ID);
        existing.setStatus("PENDING");

        when(repository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onSubscriptionEvent(event(EventTypes.Subscription.ACTIVATED));

        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        verify(repository).save(existing);
    }
}
