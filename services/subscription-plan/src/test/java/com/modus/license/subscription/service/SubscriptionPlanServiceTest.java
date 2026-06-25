package com.modus.license.subscription.service;

import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.subscription.api.dto.CreatePlanRequest;
import com.modus.license.subscription.api.dto.PlanResponse;
import com.modus.license.subscription.api.dto.UpdatePlanRequest;
import com.modus.license.subscription.api.mapper.SubscriptionPlanMapper;
import com.modus.license.subscription.domain.entity.SubscriptionPlanEntity;
import com.modus.license.subscription.domain.enums.BillingCycle;
import com.modus.license.subscription.domain.repository.SubscriptionPlanRepository;
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
@DisplayName("SubscriptionPlanService")
class SubscriptionPlanServiceTest {

    @Mock SubscriptionPlanRepository repository;
    @Mock SubscriptionPlanMapper      mapper;

    SubscriptionPlanService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPlanService(repository, mapper);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreatePlanRequest createRequest(String name) {
        return new CreatePlanRequest(name, "A plan", PlanTier.PROFESSIONAL,
                LicenseType.NAMED_USER, 50, BillingCycle.MONTHLY, 4999L, "USD",
                Set.of("feature.export"));
    }

    private SubscriptionPlanEntity planEntity(UUID id, String name, boolean active) {
        SubscriptionPlanEntity e = new SubscriptionPlanEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setName(name);
        e.setTier(PlanTier.PROFESSIONAL);
        e.setLicenseType(LicenseType.NAMED_USER);
        e.setBillingCycle(BillingCycle.MONTHLY);
        e.setPriceInCents(4999L);
        e.setActive(active);
        return e;
    }

    private PlanResponse planResponse(UUID id, String name) {
        return new PlanResponse(id, name, "A plan", PlanTier.PROFESSIONAL,
                LicenseType.NAMED_USER, 50, BillingCycle.MONTHLY, 4999L, "USD",
                true, Set.of("feature.export"), Instant.now(), Instant.now());
    }

    // ── createPlan ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPlan")
    class CreatePlan {

        @Test
        @DisplayName("saves entity and returns mapped response")
        void success() {
            UUID id = UUID.randomUUID();
            SubscriptionPlanEntity entity = planEntity(id, "Pro Monthly", true);
            PlanResponse response = planResponse(id, "Pro Monthly");

            when(repository.existsByName("Pro Monthly")).thenReturn(false);
            when(mapper.toEntity(any())).thenReturn(entity);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response);

            PlanResponse result = service.createPlan(createRequest("Pro Monthly"));

            assertThat(result.name()).isEqualTo("Pro Monthly");
            verify(repository).save(entity);
        }

        @Test
        @DisplayName("throws ConflictException when plan name already exists")
        void duplicateName_throwsConflict() {
            when(repository.existsByName("Dup Plan")).thenReturn(true);

            assertThatThrownBy(() -> service.createPlan(createRequest("Dup Plan")))
                    .isInstanceOf(ConflictException.class);

            verify(repository, never()).save(any());
        }
    }

    // ── getPlan ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlan")
    class GetPlan {

        @Test
        @DisplayName("returns response when found")
        void found() {
            UUID id = UUID.randomUUID();
            SubscriptionPlanEntity entity = planEntity(id, "Pro Monthly", true);
            PlanResponse response = planResponse(id, "Pro Monthly");

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(mapper.toResponse(entity)).thenReturn(response);

            assertThat(service.getPlan(id).id()).isEqualTo(id);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlan(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── listPlans ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listPlans")
    class ListPlans {

        @Test
        @DisplayName("activeOnly=true delegates to findByActive")
        void activeOnly() {
            UUID id = UUID.randomUUID();
            SubscriptionPlanEntity entity = planEntity(id, "Pro Monthly", true);
            when(repository.findByActive(eq(true), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(entity)));
            when(mapper.toResponse(any())).thenReturn(planResponse(id, "Pro Monthly"));

            assertThat(service.listPlans(true, Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("activeOnly=false (or null) returns all plans")
        void all() {
            UUID id = UUID.randomUUID();
            SubscriptionPlanEntity entity = planEntity(id, "Free Plan", false);
            when(repository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(entity)));
            when(mapper.toResponse(any())).thenReturn(planResponse(id, "Free Plan"));

            assertThat(service.listPlans(null, Pageable.unpaged()).getContent()).hasSize(1);
        }
    }

    // ── listByTier ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listByTier delegates to findByTierAndActive")
    void listByTier() {
        UUID id = UUID.randomUUID();
        SubscriptionPlanEntity entity = planEntity(id, "Pro Monthly", true);
        when(repository.findByTierAndActive(PlanTier.PROFESSIONAL, true))
                .thenReturn(List.of(entity));
        when(mapper.toResponse(any())).thenReturn(planResponse(id, "Pro Monthly"));

        assertThat(service.listByTier(PlanTier.PROFESSIONAL)).hasSize(1);
    }

    // ── updatePlan ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePlan")
    class UpdatePlan {

        @Test
        @DisplayName("patches non-null fields and saves")
        void success() {
            UUID id = UUID.randomUUID();
            SubscriptionPlanEntity entity = planEntity(id, "Old Plan", true);
            entity.setDescription("Old description");
            UpdatePlanRequest request = new UpdatePlanRequest("New description", 100, 9999L, false, Set.of("feature.new"));

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(planResponse(id, "Old Plan"));

            service.updatePlan(id, request);

            assertThat(entity.getDescription()).isEqualTo("New description");
            assertThat(entity.getMaxSeats()).isEqualTo(100);
            assertThat(entity.getPriceInCents()).isEqualTo(9999L);
            assertThat(entity.isActive()).isFalse();
            assertThat(entity.getFeatures()).containsExactly("feature.new");
        }

        @Test
        @DisplayName("null fields are left unchanged")
        void nullFieldsUnchanged() {
            UUID id = UUID.randomUUID();
            SubscriptionPlanEntity entity = planEntity(id, "Plan", true);
            entity.setMaxSeats(20);

            when(repository.findById(id)).thenReturn(Optional.of(entity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(planResponse(id, "Plan"));

            service.updatePlan(id, new UpdatePlanRequest(null, null, null, null, null));

            assertThat(entity.getMaxSeats()).isEqualTo(20); // unchanged
        }
    }

    // ── deactivatePlan ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivatePlan sets active=false and saves")
    void deactivatePlan() {
        UUID id = UUID.randomUUID();
        SubscriptionPlanEntity entity = planEntity(id, "Pro Monthly", true);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(planResponse(id, "Pro Monthly"));

        service.deactivatePlan(id);

        assertThat(entity.isActive()).isFalse();
    }
}
