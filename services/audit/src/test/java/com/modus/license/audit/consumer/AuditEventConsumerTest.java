package com.modus.license.audit.consumer;

import com.modus.license.audit.archive.AuditArchiveService;
import com.modus.license.audit.config.AuditProperties;
import com.modus.license.audit.domain.entity.AuditLogEntity;
import com.modus.license.audit.domain.repository.AuditLogRepository;
import com.modus.license.events.audit.AuditEvent;
import com.modus.license.events.common.EventMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventConsumer")
class AuditEventConsumerTest {

    @Mock AuditLogRepository  repository;
    @Mock AuditArchiveService archiveService;

    static final AuditProperties PROPS =
            new AuditProperties("SHA-256", false, 2555L);

    static final String TENANT_ID = UUID.randomUUID().toString();
    static final String ACTOR_ID  = UUID.randomUUID().toString();

    private AuditEventConsumer consumer(boolean withArchive) {
        Optional<AuditArchiveService> opt = withArchive
                ? Optional.of(archiveService)
                : Optional.empty();
        return new AuditEventConsumer(repository, PROPS, opt);
    }

    private AuditEvent event(String auditId) {
        EventMetadata meta = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType("AUDIT")
                .setTenantId(TENANT_ID)
                .setTimestamp(Instant.now())
                .build();

        return AuditEvent.newBuilder()
                .setMetadata(meta)
                .setAuditId(auditId)
                .setTenantId(TENANT_ID)
                .setActorId(ACTOR_ID)
                .setActorType("USER")
                .setAction("CREATE")
                .setResourceType("USER")
                .setResourceId(UUID.randomUUID().toString())
                .setOutcome("SUCCESS")
                .setServiceName("user-management")
                .build();
    }

    // ── persist ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("new event → persists entity with correct fields and SHA-256 hash")
    void newEvent_persistsEntity() {
        String auditId = UUID.randomUUID().toString();
        when(repository.existsById(UUID.fromString(auditId))).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer(false).onAuditEvent(event(auditId));

        ArgumentCaptor<AuditLogEntity> cap = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repository).save(cap.capture());
        AuditLogEntity saved = cap.getValue();
        assertThat(saved.getId()).isEqualTo(UUID.fromString(auditId));
        assertThat(saved.getTenantId()).isEqualTo(UUID.fromString(TENANT_ID));
        assertThat(saved.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getOutcome()).isEqualTo("SUCCESS");
        assertThat(saved.getServiceName()).isEqualTo("user-management");
        assertThat(saved.getContentHash()).isNotBlank().hasSize(64); // SHA-256 hex
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate event → skipped (existsById=true, no save)")
    void duplicateEvent_skipped() {
        String auditId = UUID.randomUUID().toString();
        when(repository.existsById(UUID.fromString(auditId))).thenReturn(true);

        consumer(false).onAuditEvent(event(auditId));

        verify(repository, never()).save(any());
    }

    // ── archive ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("new event with archiveService → calls archiveService.archive after persist")
    void newEvent_callsArchive() {
        String auditId = UUID.randomUUID().toString();
        when(repository.existsById(UUID.fromString(auditId))).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer(true).onAuditEvent(event(auditId));

        verify(archiveService).archive(any(AuditLogEntity.class), any(String.class));
    }

    @Test
    @DisplayName("new event without archiveService → archiveService never called")
    void newEvent_noArchive() {
        String auditId = UUID.randomUUID().toString();
        when(repository.existsById(UUID.fromString(auditId))).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer(false).onAuditEvent(event(auditId));

        verify(archiveService, never()).archive(any(), any());
    }
}
