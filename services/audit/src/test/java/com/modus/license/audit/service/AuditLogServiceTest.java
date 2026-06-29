package com.modus.license.audit.service;

import com.modus.license.audit.api.dto.AuditLogResponse;
import com.modus.license.audit.api.mapper.AuditLogMapper;
import com.modus.license.audit.domain.entity.AuditLogEntity;
import com.modus.license.audit.domain.repository.AuditLogRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService")
class AuditLogServiceTest {

    @Mock AuditLogRepository repository;
    @Mock AuditLogMapper     mapper;

    @InjectMocks AuditLogService service;

    static final UUID   TENANT_ID = TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final Instant NOW       = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextTestHelper.setDefault();
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuditLogEntity entity(UUID id, UUID tenantId) {
        AuditLogEntity e = new AuditLogEntity();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setActorId(UUID.randomUUID().toString());
        e.setActorType("USER");
        e.setAction("CREATE");
        e.setResourceType("USER");
        e.setResourceId(UUID.randomUUID().toString());
        e.setOutcome("SUCCESS");
        e.setServiceName("user-management");
        e.setRecordedAt(NOW);
        e.setContentHash("abc123");
        return e;
    }

    private AuditLogResponse response(UUID id) {
        return new AuditLogResponse(id, TENANT_ID, "actor", "USER",
                "CREATE", "USER", "resource-id",
                "SUCCESS", "user-management",
                null, null, null, null, NOW, "abc123");
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("search delegates to repository.search with tenant context and all null filters")
    void search_noFilters() {
        AuditLogEntity e = entity(UUID.randomUUID(), TENANT_ID);
        when(repository.search(eq(TENANT_ID), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(mapper.toResponse(e)).thenReturn(response(e.getId()));

        var result = service.search(null, null, null, null, null, null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        verify(repository).search(eq(TENANT_ID), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("search passes actorId and outcome filters through to repository")
    void search_withFilters() {
        String actorId = UUID.randomUUID().toString();
        when(repository.search(eq(TENANT_ID), eq(actorId), isNull(), isNull(),
                eq("SUCCESS"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.search(actorId, null, null, "SUCCESS", null, null, Pageable.unpaged());

        assertThat(result.getContent()).isEmpty();
        verify(repository).search(eq(TENANT_ID), eq(actorId), isNull(), isNull(),
                eq("SUCCESS"), isNull(), isNull(), any());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById returns response when entry belongs to current tenant")
    void getById_found_matchingTenant() {
        UUID id = UUID.randomUUID();
        AuditLogEntity e = entity(id, TENANT_ID);
        when(repository.findById(id)).thenReturn(Optional.of(e));
        when(mapper.toResponse(e)).thenReturn(response(id));

        Optional<AuditLogResponse> result = service.getById(id);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("getById returns empty when entry belongs to a different tenant")
    void getById_found_differentTenant() {
        UUID id = UUID.randomUUID();
        AuditLogEntity e = entity(id, UUID.randomUUID()); // different tenantId
        when(repository.findById(id)).thenReturn(Optional.of(e));

        Optional<AuditLogResponse> result = service.getById(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getById returns empty when entry does not exist")
    void getById_notFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.getById(id)).isEmpty();
    }
}
