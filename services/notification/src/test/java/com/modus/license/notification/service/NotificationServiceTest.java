package com.modus.license.notification.service;

import com.modus.license.notification.api.dto.NotificationResponse;
import com.modus.license.notification.api.mapper.NotificationMapper;
import com.modus.license.notification.domain.entity.NotificationEntity;
import com.modus.license.notification.domain.entity.TenantContactEntity;
import com.modus.license.notification.domain.event.NotificationEventPublisher;
import com.modus.license.notification.domain.repository.NotificationRepository;
import com.modus.license.notification.domain.repository.TenantContactRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock NotificationRepository      repository;
    @Mock TenantContactRepository     contactRepository;
    @Mock NotificationEventPublisher  publisher;
    @Mock NotificationMapper          mapper;
    @Mock EmailDispatchService        emailDispatchService;
    @Mock WebhookDispatchService      webhookDispatchService;

    @InjectMocks NotificationService service;

    static final UUID TENANT_ID = TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final UUID USER_ID   = TenantContextTestHelper.DEFAULT_USER_ID.value();

    @BeforeEach void setUp()    { TenantContextTestHelper.setDefault(); }
    @AfterEach  void tearDown() { TenantContextTestHelper.clear(); }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TenantContactEntity contact(UUID tenantId) {
        TenantContactEntity c = new TenantContactEntity();
        c.setTenantId(tenantId);
        c.setAdminEmail("admin@tenant.com");
        c.setTenantName("Test Tenant");
        c.setStatus("ACTIVE");
        c.setUpdatedAt(Instant.now());
        return c;
    }

    private NotificationEntity savedEntity(UUID id) {
        NotificationEntity e = new NotificationEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setTenantId(TENANT_ID);
        e.setNotificationType("TRIAL_STARTED");
        e.setChannel("EMAIL");
        e.setStatus("SENT");
        e.setSentAt(Instant.now());
        return e;
    }

    private NotificationResponse response(UUID id) {
        return new NotificationResponse(id, TENANT_ID, "TRIAL_STARTED", "EMAIL",
                "admin@tenant.com", "subject", "SENT", 0, null, Instant.now(), Instant.now());
    }

    // ── notifyEmail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyEmail → contact found → email sent and entity saved")
    void notifyEmail_contactFound_dispatchesAndSaves() {
        UUID notifId = UUID.randomUUID();
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.of(contact(TENANT_ID)));
        when(repository.save(any())).thenAnswer(inv -> {
            NotificationEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", notifId);
            return e;
        });

        service.notifyEmail(TENANT_ID, "TRIAL_STARTED", "Trial started", "Welcome!");

        verify(emailDispatchService).send("admin@tenant.com", "Trial started", "Welcome!");
        ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("SENT");
        assertThat(cap.getValue().getChannel()).isEqualTo("EMAIL");
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT_ID);
        verify(publisher).publish(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("notifyEmail → no contact → skipped, nothing saved")
    void notifyEmail_noContact_skips() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        service.notifyEmail(TENANT_ID, "TRIAL_STARTED", "subject", "body");

        verify(emailDispatchService, never()).send(any(), any(), any());
        verify(repository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("notifyEmail → email dispatch fails → entity saved with FAILED status")
    void notifyEmail_dispatchFails_savesFailedStatus() {
        when(contactRepository.findById(TENANT_ID)).thenReturn(Optional.of(contact(TENANT_ID)));
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailDispatchService).send(any(), any(), any());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.notifyEmail(TENANT_ID, "TRIAL_EXPIRED", "Trial expired", "body");

        ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(cap.getValue().getFailureReason()).contains("SMTP unavailable");
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("search delegates to repository with tenant from context")
    void search_delegatesToRepository() {
        UUID notifId = UUID.randomUUID();
        NotificationEntity e = savedEntity(notifId);
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(mapper.toResponse(e)).thenReturn(response(notifId));

        var result = service.search(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findByTenantIdOrderByCreatedAtDesc(TENANT_ID, Pageable.unpaged());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById returns response when found for tenant")
    void getById_found() {
        UUID notifId = UUID.randomUUID();
        NotificationEntity e = savedEntity(notifId);
        when(repository.findByIdAndTenantId(notifId, TENANT_ID)).thenReturn(Optional.of(e));
        when(mapper.toResponse(e)).thenReturn(response(notifId));

        assertThat(service.getById(notifId)).isPresent();
    }

    @Test
    @DisplayName("getById returns empty when not found")
    void getById_notFound() {
        UUID notifId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(notifId, TENANT_ID)).thenReturn(Optional.empty());

        assertThat(service.getById(notifId)).isEmpty();
    }
}
