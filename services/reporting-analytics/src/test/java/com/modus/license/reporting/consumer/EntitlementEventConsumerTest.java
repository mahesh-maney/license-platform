package com.modus.license.reporting.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.entitlement.EntitlementEvent;
import com.modus.license.reporting.domain.entity.EntitlementSnapshotEntity;
import com.modus.license.reporting.domain.repository.EntitlementSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementEventConsumer (reporting)")
class EntitlementEventConsumerTest {

    @Mock  EntitlementSnapshotRepository repository;
    @InjectMocks EntitlementEventConsumer consumer;

    static final UUID   TENANT_ID      = UUID.randomUUID();
    static final UUID   ENTITLEMENT_ID = UUID.randomUUID();
    static final UUID   SUBSCRIPTION_ID = UUID.randomUUID();
    static final UUID   PLAN_ID        = UUID.randomUUID();

    private EntitlementEvent event(String eventType) {
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
                .setSubscriptionId(SUBSCRIPTION_ID.toString())
                .setPlanId(PLAN_ID.toString())
                .setPlanTier("PROFESSIONAL")
                .setLicenseType("CONCURRENT")
                .setSeatLimit(null)
                .setFeatureKeys(List.of("FEATURE_A", "FEATURE_B"))
                .setStatus("ACTIVE")
                .setStartDate(Instant.now().minusSeconds(86400))
                .setEffectiveAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GRANTED (new) → creates snapshot with correct field values")
    void granted_createsNewSnapshot() {
        when(repository.findById(ENTITLEMENT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.GRANTED));

        ArgumentCaptor<EntitlementSnapshotEntity> cap =
                ArgumentCaptor.forClass(EntitlementSnapshotEntity.class);
        verify(repository).save(cap.capture());
        EntitlementSnapshotEntity saved = cap.getValue();
        assertThat(saved.getEntitlementId()).isEqualTo(ENTITLEMENT_ID);
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getPlanTier()).isEqualTo("PROFESSIONAL");
        assertThat(saved.getLicenseType()).isEqualTo("CONCURRENT");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getFeatureKeys()).isEqualTo("FEATURE_A,FEATURE_B");
    }

    @Test
    @DisplayName("UPDATED (existing) → updates snapshot in-place")
    void updated_updatesExistingSnapshot() {
        EntitlementSnapshotEntity existing = new EntitlementSnapshotEntity();
        existing.setEntitlementId(ENTITLEMENT_ID);
        existing.setStatus("PENDING");

        when(repository.findById(ENTITLEMENT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onEntitlementEvent(event(EventTypes.Entitlement.UPDATED));

        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getFeatureKeys()).isEqualTo("FEATURE_A,FEATURE_B");
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("REVOKED → upserts snapshot with REVOKED status")
    void revoked_upsertsSnapshot() {
        EntitlementEvent revoked = EntitlementEvent.newBuilder()
                .setMetadata(EventMetadata.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setEventType(EventTypes.Entitlement.REVOKED)
                        .setTenantId(TENANT_ID.toString())
                        .setTimestamp(Instant.now())
                        .build())
                .setEntitlementId(ENTITLEMENT_ID.toString())
                .setTenantId(TENANT_ID.toString())
                .setSubscriptionId(SUBSCRIPTION_ID.toString())
                .setPlanId(PLAN_ID.toString())
                .setPlanTier("PROFESSIONAL")
                .setLicenseType("CONCURRENT")
                .setFeatureKeys(List.of())
                .setStatus("REVOKED")
                .setStartDate(Instant.now().minusSeconds(86400))
                .setEffectiveAt(Instant.now())
                .build();

        when(repository.findById(ENTITLEMENT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onEntitlementEvent(revoked);

        ArgumentCaptor<EntitlementSnapshotEntity> cap =
                ArgumentCaptor.forClass(EntitlementSnapshotEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("REVOKED");
    }
}
