package com.modus.license.tenant.service;

import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.domain.enums.TenantStatus;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.tenant.api.dto.CreateTenantRequest;
import com.modus.license.tenant.api.dto.TenantResponse;
import com.modus.license.tenant.api.dto.UpdateTenantRequest;
import com.modus.license.tenant.api.mapper.TenantMapper;
import com.modus.license.tenant.domain.entity.TenantEntity;
import com.modus.license.tenant.domain.event.TenantEventPublisher;
import com.modus.license.tenant.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService")
class TenantServiceTest {

    @Mock TenantRepository    repository;
    @Mock TenantMapper        mapper;
    @Mock TenantEventPublisher eventPublisher;

    TenantService service;

    @BeforeEach
    void setUp() {
        service = new TenantService(repository, mapper, eventPublisher);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreateTenantRequest createRequest(String slug, PlanTier planTier) {
        return new CreateTenantRequest(slug, "Test Corp", "Test Display",
                "admin@" + slug + ".com", planTier, "eastus");
    }

    private TenantEntity blankEntity() {
        return new TenantEntity();
    }

    private TenantEntity savedEntity(UUID id, TenantStatus status) {
        TenantEntity e = new TenantEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setSlug("acme-corp");
        e.setName("Test Corp");
        e.setAdminEmail("admin@acme-corp.com");
        e.setStatus(status);
        e.setPlanTier(PlanTier.PROFESSIONAL);
        e.setRegion("eastus");
        return e;
    }

    private TenantResponse stubResponse(UUID id, String slug, TenantStatus status) {
        return new TenantResponse(id, slug, "Test Corp", null,
                "admin@acme-corp.com", status, PlanTier.PROFESSIONAL,
                "eastus", null, Instant.now(), Instant.now());
    }

    // ── createTenant ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createTenant")
    class CreateTenant {

        @Test
        @DisplayName("FREE plan → status ACTIVE, publishes CREATED event")
        void freePlan_setsActive() {
            CreateTenantRequest request = createRequest("acme-corp", PlanTier.FREE);
            TenantEntity entity = blankEntity();
            UUID id = UUID.randomUUID();

            when(repository.existsBySlug("acme-corp")).thenReturn(false);
            when(repository.existsByAdminEmail(any())).thenReturn(false);
            when(mapper.toEntity(request)).thenReturn(entity);
            when(repository.save(any())).thenAnswer(inv -> {
                TenantEntity saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", id);
                return saved;
            });
            when(mapper.toResponse(any())).thenReturn(stubResponse(id, "acme-corp", TenantStatus.ACTIVE));

            service.createTenant(request);

            assertThat(entity.getStatus()).isEqualTo(TenantStatus.ACTIVE);
            assertThat(entity.getTrialEndsAt()).isNull();
            verify(eventPublisher).publishCreated(entity);
            verify(eventPublisher, never()).publishTrialStarted(any());
        }

        @Test
        @DisplayName("PROFESSIONAL plan → status TRIAL, trialEndsAt set 30 days out, publishes TRIAL_STARTED")
        void professionalPlan_setsTrial() {
            CreateTenantRequest request = createRequest("beta-corp", PlanTier.PROFESSIONAL);
            TenantEntity entity = blankEntity();

            when(repository.existsBySlug("beta-corp")).thenReturn(false);
            when(repository.existsByAdminEmail(any())).thenReturn(false);
            when(mapper.toEntity(request)).thenReturn(entity);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(stubResponse(UUID.randomUUID(), "beta-corp", TenantStatus.TRIAL));

            service.createTenant(request);

            assertThat(entity.getStatus()).isEqualTo(TenantStatus.TRIAL);
            assertThat(entity.getTrialEndsAt()).isAfter(Instant.now());
            verify(eventPublisher).publishTrialStarted(entity);
            verify(eventPublisher, never()).publishCreated(any());
        }

        @Test
        @DisplayName("throws ConflictException when slug already exists")
        void duplicateSlug_throwsConflict() {
            when(repository.existsBySlug("dup-slug")).thenReturn(true);

            assertThatThrownBy(() -> service.createTenant(createRequest("dup-slug", PlanTier.FREE)))
                    .isInstanceOf(ConflictException.class);

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishCreated(any());
        }

        @Test
        @DisplayName("throws ConflictException when adminEmail already registered")
        void duplicateEmail_throwsConflict() {
            when(repository.existsBySlug("unique-slug")).thenReturn(false);
            when(repository.existsByAdminEmail("admin@unique-slug.com")).thenReturn(true);

            assertThatThrownBy(() -> service.createTenant(createRequest("unique-slug", PlanTier.FREE)))
                    .isInstanceOf(ConflictException.class);

            verify(repository, never()).save(any());
        }
    }

    // ── getTenant ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTenant")
    class GetTenant {

        @Test
        @DisplayName("returns response when tenant found")
        void found() {
            UUID id = UUID.randomUUID();
            TenantEntity entity = savedEntity(id, TenantStatus.ACTIVE);
            TenantResponse response = stubResponse(id, "acme-corp", TenantStatus.ACTIVE);

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(mapper.toResponse(entity)).thenReturn(response);

            TenantResponse result = service.getTenant(id);
            assertThat(result.id()).isEqualTo(id);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTenant(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getTenantBySlug ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTenantBySlug")
    class GetTenantBySlug {

        @Test
        @DisplayName("returns response when found by slug")
        void found() {
            UUID id = UUID.randomUUID();
            TenantEntity entity = savedEntity(id, TenantStatus.ACTIVE);
            TenantResponse response = stubResponse(id, "acme-corp", TenantStatus.ACTIVE);

            when(repository.findBySlug("acme-corp")).thenReturn(Optional.of(entity));
            when(mapper.toResponse(entity)).thenReturn(response);

            TenantResponse result = service.getTenantBySlug("acme-corp");
            assertThat(result.slug()).isEqualTo("acme-corp");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when slug not found")
        void notFound() {
            when(repository.findBySlug("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTenantBySlug("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── updateTenant ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTenant")
    class UpdateTenant {

        @Test
        @DisplayName("patches non-null fields and publishes UPDATED event")
        void success() {
            UUID id = UUID.randomUUID();
            TenantEntity entity = savedEntity(id, TenantStatus.ACTIVE);
            UpdateTenantRequest request = new UpdateTenantRequest("New Name", null, PlanTier.ENTERPRISE, null);

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(stubResponse(id, "acme-corp", TenantStatus.ACTIVE));

            service.updateTenant(id, request);

            assertThat(entity.getName()).isEqualTo("New Name");
            assertThat(entity.getPlanTier()).isEqualTo(PlanTier.ENTERPRISE);
            assertThat(entity.getDisplayName()).isNull(); // null → not changed
            verify(eventPublisher).publishUpdated(entity);
        }
    }

    // ── suspendTenant ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("suspendTenant")
    class SuspendTenant {

        @Test
        @DisplayName("sets status to SUSPENDED and publishes event with previous status")
        void success() {
            UUID id = UUID.randomUUID();
            TenantEntity entity = savedEntity(id, TenantStatus.ACTIVE);

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(stubResponse(id, "acme-corp", TenantStatus.SUSPENDED));

            service.suspendTenant(id);

            assertThat(entity.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
            verify(eventPublisher).publishSuspended(eq(entity), eq("ACTIVE"));
        }
    }

    // ── activateTenant ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("activateTenant")
    class ActivateTenant {

        @Test
        @DisplayName("sets status to ACTIVE and publishes event with previous status")
        void success() {
            UUID id = UUID.randomUUID();
            TenantEntity entity = savedEntity(id, TenantStatus.SUSPENDED);

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(stubResponse(id, "acme-corp", TenantStatus.ACTIVE));

            service.activateTenant(id);

            assertThat(entity.getStatus()).isEqualTo(TenantStatus.ACTIVE);
            verify(eventPublisher).publishActivated(eq(entity), eq("SUSPENDED"));
        }
    }

    // ── deleteTenant ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTenant")
    class DeleteTenant {

        @Test
        @DisplayName("soft-deletes by setting INACTIVE and publishes DELETED event")
        void success() {
            UUID id = UUID.randomUUID();
            TenantEntity entity = savedEntity(id, TenantStatus.ACTIVE);

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.deleteTenant(id);

            assertThat(entity.getStatus()).isEqualTo(TenantStatus.INACTIVE);
            verify(eventPublisher).publishDeleted(entity);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when tenant not found")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteTenant(id))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(eventPublisher, never()).publishDeleted(any());
        }
    }
}
