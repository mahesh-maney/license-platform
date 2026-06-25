package com.modus.license.entitlement.service;

import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.entitlement.api.dto.CreateEntitlementRequest;
import com.modus.license.entitlement.api.dto.EntitlementResponse;
import com.modus.license.entitlement.api.dto.UpdateEntitlementRequest;
import com.modus.license.entitlement.api.mapper.EntitlementMapper;
import com.modus.license.entitlement.domain.cache.EntitlementCacheService;
import com.modus.license.entitlement.domain.entity.EntitlementEntity;
import com.modus.license.entitlement.domain.event.EntitlementEventPublisher;
import com.modus.license.entitlement.domain.repository.EntitlementRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementService")
class EntitlementServiceTest {

    @Mock EntitlementRepository     repository;
    @Mock EntitlementMapper         mapper;
    @Mock EntitlementEventPublisher eventPublisher;
    @Mock EntitlementCacheService   cacheService;

    EntitlementService service;

    static final UUID TENANT_ID = TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final UUID PLAN_ID   = UUID.randomUUID();
    static final UUID SUB_ID    = UUID.randomUUID();

    private static final List<EntitlementStatus> ACTIVE_STATUSES =
            List.of(EntitlementStatus.ACTIVE, EntitlementStatus.PENDING);

    @BeforeEach
    void setUp() {
        service = new EntitlementService(repository, mapper, eventPublisher, cacheService);
        TenantContextTestHelper.setDefault();
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EntitlementEntity entity(UUID id, EntitlementStatus status) {
        EntitlementEntity e = new EntitlementEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setTenantId(TENANT_ID);
        e.setSubscriptionId(SUB_ID);
        e.setPlanId(PLAN_ID);
        e.setPlanTier(PlanTier.PROFESSIONAL);
        e.setLicenseType(LicenseType.NAMED_USER);
        e.setSeatLimit(50);
        e.setStatus(status);
        e.setStartDate(Instant.now());
        return e;
    }

    private EntitlementResponse response(UUID id, EntitlementStatus status) {
        return new EntitlementResponse(id, TENANT_ID, SUB_ID, PLAN_ID,
                PlanTier.PROFESSIONAL, LicenseType.NAMED_USER, 50,
                Set.of("feature.read"), status,
                Instant.now(), null, Instant.now(), Instant.now());
    }

    private CreateEntitlementRequest createRequest() {
        return new CreateEntitlementRequest(TENANT_ID, SUB_ID, PLAN_ID,
                PlanTier.PROFESSIONAL, LicenseType.NAMED_USER, 50,
                Set.of("feature.read"), Instant.now(), null);
    }

    // ── grantEntitlement ─────────────────────────────────────────────────────

    @Test
    @DisplayName("grantEntitlement saves, sets ACTIVE, publishes, and caches")
    void grantEntitlement_success() {
        UUID id = UUID.randomUUID();
        EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
        EntitlementResponse resp = response(id, EntitlementStatus.ACTIVE);

        when(mapper.toEntity(any())).thenReturn(e);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(resp);

        EntitlementResponse result = service.grantEntitlement(createRequest());

        assertThat(e.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(result.status()).isEqualTo(EntitlementStatus.ACTIVE);
        verify(eventPublisher).publishGranted(e);
        verify(cacheService).cache(TENANT_ID, resp);
    }

    // ── getEntitlement ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getEntitlement")
    class GetEntitlement {

        @Test
        @DisplayName("returns response when found")
        void found() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            EntitlementResponse resp = response(id, EntitlementStatus.ACTIVE);

            when(repository.findById(id)).thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(resp);

            assertThat(service.getEntitlement(id).id()).isEqualTo(id);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEntitlement(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getActiveEntitlement ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getActiveEntitlement")
    class GetActiveEntitlement {

        @Test
        @DisplayName("returns cached response on cache hit (no DB call)")
        void cacheHit() {
            UUID id = UUID.randomUUID();
            EntitlementResponse cached = response(id, EntitlementStatus.ACTIVE);
            when(cacheService.get(TENANT_ID)).thenReturn(Optional.of(cached));

            EntitlementResponse result = service.getActiveEntitlement();

            assertThat(result).isEqualTo(cached);
            verify(repository, never()).findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(any(), any());
        }

        @Test
        @DisplayName("fetches from DB and caches on cache miss")
        void cacheMiss() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            EntitlementResponse resp = response(id, EntitlementStatus.ACTIVE);

            when(cacheService.get(TENANT_ID)).thenReturn(Optional.empty());
            when(repository.findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(eq(TENANT_ID), eq(ACTIVE_STATUSES)))
                    .thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(resp);

            EntitlementResponse result = service.getActiveEntitlement();

            assertThat(result).isEqualTo(resp);
            verify(cacheService).cache(TENANT_ID, resp);
        }

        @Test
        @DisplayName("throws ModusException when no active entitlement found")
        void notFound() {
            when(cacheService.get(TENANT_ID)).thenReturn(Optional.empty());
            when(repository.findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(eq(TENANT_ID), eq(ACTIVE_STATUSES)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getActiveEntitlement())
                    .isInstanceOf(ModusException.class);
        }
    }

    // ── listEntitlements ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listEntitlements delegates to repo with current tenantId")
    void listEntitlements() {
        UUID id = UUID.randomUUID();
        EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
        when(repository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.ACTIVE));

        assertThat(service.listEntitlements(Pageable.unpaged()).getContent()).hasSize(1);
    }

    // ── updateEntitlement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateEntitlement")
    class UpdateEntitlement {

        @Test
        @DisplayName("patches non-null fields, saves, caches, and publishes UPDATED")
        void success() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            e.setFeatureKeys(new HashSet<>(Set.of("feature.old")));
            EntitlementResponse resp = response(id, EntitlementStatus.ACTIVE);
            UpdateEntitlementRequest req = new UpdateEntitlementRequest(
                    Set.of("feature.new"), 100, Instant.now().plusSeconds(3600));

            when(repository.findById(id)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(resp);

            service.updateEntitlement(id, req);

            assertThat(e.getFeatureKeys()).containsExactly("feature.new");
            assertThat(e.getSeatLimit()).isEqualTo(100);
            assertThat(e.getEndDate()).isNotNull();
            verify(cacheService).cache(eq(TENANT_ID), any());
            verify(eventPublisher).publishUpdated(e);
        }

        @Test
        @DisplayName("null fields are left unchanged")
        void nullFieldsUnchanged() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            e.setSeatLimit(20);

            when(repository.findById(id)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.ACTIVE));

            service.updateEntitlement(id, new UpdateEntitlementRequest(null, null, null));

            assertThat(e.getSeatLimit()).isEqualTo(20);
        }
    }

    // ── revokeEntitlement ─────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeEntitlement sets REVOKED, evicts cache, publishes revoked")
    void revokeEntitlement() {
        UUID id = UUID.randomUUID();
        EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);

        when(repository.findById(id)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.REVOKED));

        service.revokeEntitlement(id);

        assertThat(e.getStatus()).isEqualTo(EntitlementStatus.REVOKED);
        verify(cacheService).evict(TENANT_ID);
        verify(eventPublisher).publishRevoked(e, "ACTIVE");
    }

    // ── suspendEntitlement ────────────────────────────────────────────────────

    @Test
    @DisplayName("suspendEntitlement sets SUSPENDED, evicts cache, publishes suspended")
    void suspendEntitlement() {
        UUID id = UUID.randomUUID();
        EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);

        when(repository.findById(id)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.SUSPENDED));

        service.suspendEntitlement(id);

        assertThat(e.getStatus()).isEqualTo(EntitlementStatus.SUSPENDED);
        verify(cacheService).evict(TENANT_ID);
        verify(eventPublisher).publishSuspended(e, "ACTIVE");
    }

    // ── processSubscriptionCreated ────────────────────────────────────────────

    @Test
    @DisplayName("processSubscriptionCreated saves entity, publishes GRANTED, and caches")
    void processSubscriptionCreated() {
        UUID newId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> {
            EntitlementEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", newId);
            return e;
        });
        when(mapper.toResponse(any())).thenReturn(response(newId, EntitlementStatus.ACTIVE));

        service.processSubscriptionCreated(TENANT_ID, SUB_ID, PLAN_ID,
                PlanTier.PROFESSIONAL, LicenseType.NAMED_USER, 50,
                Instant.now(), null);

        verify(repository).save(any(EntitlementEntity.class));
        verify(eventPublisher).publishGranted(any(EntitlementEntity.class));
        verify(cacheService).cache(eq(TENANT_ID), any());
    }

    // ── processSubscriptionPlanChanged ────────────────────────────────────────

    @Nested
    @DisplayName("processSubscriptionPlanChanged")
    class ProcessSubscriptionPlanChanged {

        @Test
        @DisplayName("updates plan fields and publishes UPDATED when subscription found")
        void found() {
            UUID newPlanId = UUID.randomUUID();
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);

            when(repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(SUB_ID)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.ACTIVE));

            service.processSubscriptionPlanChanged(SUB_ID, newPlanId,
                    PlanTier.ENTERPRISE, LicenseType.NAMED_USER, 100);

            assertThat(e.getPlanId()).isEqualTo(newPlanId);
            assertThat(e.getPlanTier()).isEqualTo(PlanTier.ENTERPRISE);
            assertThat(e.getSeatLimit()).isEqualTo(100);
            verify(eventPublisher).publishUpdated(e);
        }

        @Test
        @DisplayName("does nothing when subscription not found")
        void notFound() {
            when(repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(SUB_ID)).thenReturn(Optional.empty());

            service.processSubscriptionPlanChanged(SUB_ID, PLAN_ID,
                    PlanTier.ENTERPRISE, LicenseType.NAMED_USER, 100);

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishUpdated(any());
        }
    }

    // ── processSubscriptionStatusChanged ──────────────────────────────────────

    @Nested
    @DisplayName("processSubscriptionStatusChanged")
    class ProcessSubscriptionStatusChanged {

        @Test
        @DisplayName("REVOKED → sets status and publishes publishRevoked")
        void revoked() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            when(repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(SUB_ID)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.REVOKED);

            assertThat(e.getStatus()).isEqualTo(EntitlementStatus.REVOKED);
            verify(cacheService).evict(TENANT_ID);
            verify(eventPublisher).publishRevoked(e, "ACTIVE");
        }

        @Test
        @DisplayName("SUSPENDED → sets status and publishes publishSuspended")
        void suspended() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            when(repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(SUB_ID)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.SUSPENDED);

            assertThat(e.getStatus()).isEqualTo(EntitlementStatus.SUSPENDED);
            verify(eventPublisher).publishSuspended(e, "ACTIVE");
        }

        @Test
        @DisplayName("EXPIRED → sets status and publishes publishExpired")
        void expired() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            when(repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(SUB_ID)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.EXPIRED);

            assertThat(e.getStatus()).isEqualTo(EntitlementStatus.EXPIRED);
            verify(eventPublisher).publishExpired(e);
        }

        @Test
        @DisplayName("ACTIVE → sets status and publishes publishRenewed")
        void renewed() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.EXPIRED);
            when(repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(SUB_ID)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processSubscriptionStatusChanged(SUB_ID, EntitlementStatus.ACTIVE);

            assertThat(e.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
            verify(eventPublisher).publishRenewed(e);
        }
    }

    // ── processTenantSuspended ────────────────────────────────────────────────

    @Test
    @DisplayName("processTenantSuspended suspends all ACTIVE entitlements and evicts cache")
    void processTenantSuspended() {
        UUID id = UUID.randomUUID();
        EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
        when(repository.findByTenantIdAndStatus(TENANT_ID, EntitlementStatus.ACTIVE))
                .thenReturn(List.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processTenantSuspended(TENANT_ID);

        assertThat(e.getStatus()).isEqualTo(EntitlementStatus.SUSPENDED);
        verify(eventPublisher).publishSuspended(e, "ACTIVE");
        verify(cacheService).evict(TENANT_ID);
    }

    // ── processTenantActivated ────────────────────────────────────────────────

    @Test
    @DisplayName("processTenantActivated activates all SUSPENDED entitlements and caches")
    void processTenantActivated() {
        UUID id = UUID.randomUUID();
        EntitlementEntity e = entity(id, EntitlementStatus.SUSPENDED);
        when(repository.findByTenantIdAndStatus(TENANT_ID, EntitlementStatus.SUSPENDED))
                .thenReturn(List.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.ACTIVE));

        service.processTenantActivated(TENANT_ID);

        assertThat(e.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        verify(eventPublisher).publishRenewed(e);
        verify(cacheService).cache(eq(TENANT_ID), any());
    }

    // ── processFeatureEnabled / processFeatureDisabled ────────────────────────

    @Nested
    @DisplayName("processFeatureEnabled")
    class ProcessFeatureEnabled {

        @Test
        @DisplayName("adds new feature key, saves, caches, and publishes UPDATED")
        void addsNewKey() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            e.setFeatureKeys(new HashSet<>(Set.of("feature.existing")));

            when(repository.findByTenantIdAndStatus(TENANT_ID, EntitlementStatus.ACTIVE))
                    .thenReturn(List.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.ACTIVE));

            service.processFeatureEnabled(TENANT_ID, "feature.new");

            assertThat(e.getFeatureKeys()).contains("feature.new");
            verify(eventPublisher).publishUpdated(e);
            verify(cacheService).cache(eq(TENANT_ID), any());
        }

        @Test
        @DisplayName("no-op when feature key is already present")
        void alreadyPresent() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            e.setFeatureKeys(new HashSet<>(Set.of("feature.existing")));

            when(repository.findByTenantIdAndStatus(TENANT_ID, EntitlementStatus.ACTIVE))
                    .thenReturn(List.of(e));

            service.processFeatureEnabled(TENANT_ID, "feature.existing");

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishUpdated(any());
        }
    }

    @Nested
    @DisplayName("processFeatureDisabled")
    class ProcessFeatureDisabled {

        @Test
        @DisplayName("removes existing feature key, saves, caches, and publishes UPDATED")
        void removesKey() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            e.setFeatureKeys(new HashSet<>(Set.of("feature.export", "feature.read")));

            when(repository.findByTenantIdAndStatus(TENANT_ID, EntitlementStatus.ACTIVE))
                    .thenReturn(List.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(id, EntitlementStatus.ACTIVE));

            service.processFeatureDisabled(TENANT_ID, "feature.export");

            assertThat(e.getFeatureKeys()).doesNotContain("feature.export");
            verify(eventPublisher).publishUpdated(e);
        }

        @Test
        @DisplayName("no-op when feature key is not present")
        void notPresent() {
            UUID id = UUID.randomUUID();
            EntitlementEntity e = entity(id, EntitlementStatus.ACTIVE);
            e.setFeatureKeys(new HashSet<>(Set.of("feature.read")));

            when(repository.findByTenantIdAndStatus(TENANT_ID, EntitlementStatus.ACTIVE))
                    .thenReturn(List.of(e));

            service.processFeatureDisabled(TENANT_ID, "feature.missing");

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishUpdated(any());
        }
    }
}
