package com.modus.license.notification.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.tenant.TenantEvent;
import com.modus.license.notification.domain.entity.TenantContactEntity;
import com.modus.license.notification.domain.repository.TenantContactRepository;
import com.modus.license.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantEventConsumer (notification)")
class TenantEventConsumerTest {

    @Mock TenantContactRepository contactRepository;
    @Mock NotificationService     notificationService;

    @InjectMocks TenantEventConsumer consumer;

    static final UUID TENANT_ID = UUID.randomUUID();

    private TenantEvent event(String eventType) {
        EventMetadata meta = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID.toString())
                .setTimestamp(Instant.now())
                .build();

        return TenantEvent.newBuilder()
                .setMetadata(meta)
                .setTenantId(TENANT_ID.toString())
                .setSlug("acme-corp")
                .setName("Acme Corp")
                .setAdminEmail("admin@acme.com")
                .setStatus("ACTIVE")
                .setPlanTier(null)
                .setRegion(null)
                .setPreviousStatus(null)
                .setTrialEndsAt(null)
                .setEffectiveAt(Instant.now())
                .build();
    }

    // ── contact upsert ────────────────────────────────────────────────────────

    @Test
    @DisplayName("any event → upserts TenantContact with correct fields")
    void anyEvent_upsertsContact() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTenantEvent(event(EventTypes.Tenant.CREATED));

        ArgumentCaptor<TenantContactEntity> cap =
                ArgumentCaptor.forClass(TenantContactEntity.class);
        verify(contactRepository).save(cap.capture());
        TenantContactEntity saved = cap.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getAdminEmail()).isEqualTo("admin@acme.com");
        assertThat(saved.getTenantName()).isEqualTo("Acme Corp");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("event with existing contact → updates in-place")
    void existingContact_updatesInPlace() {
        TenantContactEntity existing = new TenantContactEntity();
        existing.setTenantId(TENANT_ID);
        existing.setAdminEmail("old@acme.com");
        existing.setUpdatedAt(Instant.now().minusSeconds(3600));
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTenantEvent(event(EventTypes.Tenant.ACTIVATED));

        assertThat(existing.getAdminEmail()).isEqualTo("admin@acme.com");
        verify(contactRepository).save(existing);
    }

    // ── notification routing ──────────────────────────────────────────────────

    @Test
    @DisplayName("TRIAL_STARTED → notifyEmail with TRIAL_STARTED type")
    void trialStarted_sendsNotification() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTenantEvent(event(EventTypes.Tenant.TRIAL_STARTED));

        verify(notificationService).notifyEmail(
                eq(TENANT_ID), eq("TRIAL_STARTED"), anyString(), anyString());
    }

    @Test
    @DisplayName("TRIAL_EXPIRED → notifyEmail with TRIAL_EXPIRED type")
    void trialExpired_sendsNotification() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTenantEvent(event(EventTypes.Tenant.TRIAL_EXPIRED));

        verify(notificationService).notifyEmail(
                eq(TENANT_ID), eq("TRIAL_EXPIRED"), anyString(), anyString());
    }

    @Test
    @DisplayName("SUSPENDED → notifyEmail with TENANT_SUSPENDED type")
    void suspended_sendsNotification() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTenantEvent(event(EventTypes.Tenant.SUSPENDED));

        verify(notificationService).notifyEmail(
                eq(TENANT_ID), eq("TENANT_SUSPENDED"), anyString(), anyString());
    }

    @Test
    @DisplayName("ACTIVATED → notifyEmail with TENANT_ACTIVATED type")
    void activated_sendsNotification() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTenantEvent(event(EventTypes.Tenant.ACTIVATED));

        verify(notificationService).notifyEmail(
                eq(TENANT_ID), eq("TENANT_ACTIVATED"), anyString(), anyString());
    }

    @Test
    @DisplayName("CREATED → no notification sent")
    void created_noNotification() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onTenantEvent(event(EventTypes.Tenant.CREATED));

        verify(notificationService, never()).notifyEmail(any(), any(), any(), any());
    }
}
