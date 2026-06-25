package com.modus.license.subscription.service;

import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.subscription.api.dto.CreateSubscriptionRequest;
import com.modus.license.subscription.api.dto.PlanResponse;
import com.modus.license.subscription.api.dto.SubscriptionResponse;
import com.modus.license.subscription.api.mapper.SubscriptionMapper;
import com.modus.license.subscription.domain.entity.SubscriptionEntity;
import com.modus.license.subscription.domain.entity.SubscriptionPlanEntity;
import com.modus.license.subscription.domain.enums.BillingCycle;
import com.modus.license.subscription.domain.enums.SubscriptionStatus;
import com.modus.license.subscription.domain.event.SubscriptionEventPublisher;
import com.modus.license.subscription.domain.repository.SubscriptionRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@DisplayName("SubscriptionService")
class SubscriptionServiceTest {

    @Mock SubscriptionRepository     repository;
    @Mock SubscriptionMapper         mapper;
    @Mock SubscriptionPlanService    planService;
    @Mock SubscriptionEventPublisher eventPublisher;

    SubscriptionService service;

    static final UUID TENANT_ID =
            TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final UUID PLAN_ID   = UUID.randomUUID();

    private static final List<SubscriptionStatus> ACTIVE_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL);

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(repository, mapper, planService, eventPublisher);
        TenantContextTestHelper.setDefault();   // populates TenantContextHolder for currentTenantId()
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SubscriptionPlanEntity plan(UUID planId, PlanTier tier) {
        SubscriptionPlanEntity p = new SubscriptionPlanEntity();
        ReflectionTestUtils.setField(p, "id", planId);
        p.setName("Pro Plan");
        p.setTier(tier);
        p.setLicenseType(LicenseType.NAMED_USER);
        p.setBillingCycle(BillingCycle.MONTHLY);
        p.setPriceInCents(4999L);
        p.setMaxSeats(50);
        p.setActive(true);
        return p;
    }

    private SubscriptionEntity sub(UUID subId, SubscriptionStatus status, SubscriptionPlanEntity plan) {
        SubscriptionEntity e = new SubscriptionEntity();
        ReflectionTestUtils.setField(e, "id", subId);
        e.setTenantId(TENANT_ID);
        e.setPlan(plan);
        e.setStatus(status);
        e.setBillingCycle(BillingCycle.MONTHLY);
        e.setStartDate(Instant.now());
        return e;
    }

    private SubscriptionResponse stubResponse(UUID subId, SubscriptionStatus status) {
        PlanResponse planResp = new PlanResponse(PLAN_ID, "Pro Plan", null, PlanTier.PROFESSIONAL,
                LicenseType.NAMED_USER, 50, BillingCycle.MONTHLY, 4999L, "USD",
                true, Set.of(), Instant.now(), Instant.now());
        return new SubscriptionResponse(subId, TENANT_ID, planResp, status,
                null, 50, BillingCycle.MONTHLY, Instant.now(), null,
                null, null, Instant.now(), Instant.now());
    }

    private CreateSubscriptionRequest createRequest() {
        return new CreateSubscriptionRequest(PLAN_ID, BillingCycle.MONTHLY, null, Instant.now(), null);
    }

    // ── createSubscription ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createSubscription")
    class CreateSubscription {

        @Test
        @DisplayName("creates subscription for tenant with no existing active sub")
        void success() {
            UUID subId = UUID.randomUUID();
            SubscriptionPlanEntity planEntity = plan(PLAN_ID, PlanTier.PROFESSIONAL);

            when(repository.existsByTenantIdAndStatusIn(TENANT_ID, ACTIVE_STATUSES)).thenReturn(false);
            when(planService.findOrThrow(PLAN_ID)).thenReturn(planEntity);
            when(repository.save(any())).thenAnswer(inv -> {
                SubscriptionEntity e = inv.getArgument(0);
                ReflectionTestUtils.setField(e, "id", subId);
                return e;
            });
            when(mapper.toResponse(any())).thenReturn(stubResponse(subId, SubscriptionStatus.ACTIVE));

            SubscriptionResponse result = service.createSubscription(createRequest());

            assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(eventPublisher).publishCreated(any(SubscriptionEntity.class));
        }

        @Test
        @DisplayName("throws ConflictException when tenant already has an active subscription")
        void alreadyActive_throwsConflict() {
            when(repository.existsByTenantIdAndStatusIn(TENANT_ID, ACTIVE_STATUSES)).thenReturn(true);

            assertThatThrownBy(() -> service.createSubscription(createRequest()))
                    .isInstanceOf(ConflictException.class);

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishCreated(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when planId does not exist")
        void planNotFound() {
            when(repository.existsByTenantIdAndStatusIn(TENANT_ID, ACTIVE_STATUSES)).thenReturn(false);
            when(planService.findOrThrow(PLAN_ID)).thenThrow(ResourceNotFoundException.plan(PLAN_ID.toString()));

            assertThatThrownBy(() -> service.createSubscription(createRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getSubscription ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSubscription")
    class GetSubscription {

        @Test
        @DisplayName("returns response when found")
        void found() {
            UUID subId = UUID.randomUUID();
            SubscriptionPlanEntity planEntity = plan(PLAN_ID, PlanTier.PROFESSIONAL);
            SubscriptionEntity subEntity = sub(subId, SubscriptionStatus.ACTIVE, planEntity);

            when(repository.findById(subId)).thenReturn(Optional.of(subEntity));
            when(mapper.toResponse(subEntity)).thenReturn(stubResponse(subId, SubscriptionStatus.ACTIVE));

            assertThat(service.getSubscription(subId).id()).isEqualTo(subId);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            UUID subId = UUID.randomUUID();
            when(repository.findById(subId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSubscription(subId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getActiveSubscription ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getActiveSubscription")
    class GetActiveSubscription {

        @Test
        @DisplayName("returns active subscription for current tenant")
        void found() {
            UUID subId = UUID.randomUUID();
            SubscriptionPlanEntity planEntity = plan(PLAN_ID, PlanTier.PROFESSIONAL);
            SubscriptionEntity subEntity = sub(subId, SubscriptionStatus.ACTIVE, planEntity);

            when(repository.findByTenantIdAndStatusIn(eq(TENANT_ID), eq(ACTIVE_STATUSES)))
                    .thenReturn(Optional.of(subEntity));
            when(mapper.toResponse(subEntity)).thenReturn(stubResponse(subId, SubscriptionStatus.ACTIVE));

            assertThat(service.getActiveSubscription().status()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("throws ModusException when no active subscription found")
        void notFound() {
            when(repository.findByTenantIdAndStatusIn(eq(TENANT_ID), eq(ACTIVE_STATUSES)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getActiveSubscription())
                    .isInstanceOf(ModusException.class);
        }
    }

    // ── changePlan ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePlan")
    class ChangePlan {

        @Test
        @DisplayName("publishes UPGRADED when new plan tier >= current tier")
        void upgrade() {
            UUID subId = UUID.randomUUID();
            UUID newPlanId = UUID.randomUUID();
            SubscriptionPlanEntity oldPlan = plan(PLAN_ID, PlanTier.PROFESSIONAL);
            SubscriptionPlanEntity newPlan = plan(newPlanId, PlanTier.ENTERPRISE);
            SubscriptionEntity subEntity = sub(subId, SubscriptionStatus.ACTIVE, oldPlan);

            when(repository.findById(subId)).thenReturn(Optional.of(subEntity));
            when(planService.findOrThrow(newPlanId)).thenReturn(newPlan);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(stubResponse(subId, SubscriptionStatus.ACTIVE));

            service.changePlan(subId, newPlanId);

            verify(eventPublisher).publishUpgraded(any(), eq(oldPlan));
            verify(eventPublisher, never()).publishDowngraded(any(), any());
        }

        @Test
        @DisplayName("publishes DOWNGRADED when new plan tier < current tier")
        void downgrade() {
            UUID subId = UUID.randomUUID();
            UUID newPlanId = UUID.randomUUID();
            SubscriptionPlanEntity oldPlan = plan(PLAN_ID, PlanTier.ENTERPRISE);
            SubscriptionPlanEntity newPlan = plan(newPlanId, PlanTier.STARTER);
            SubscriptionEntity subEntity = sub(subId, SubscriptionStatus.ACTIVE, oldPlan);

            when(repository.findById(subId)).thenReturn(Optional.of(subEntity));
            when(planService.findOrThrow(newPlanId)).thenReturn(newPlan);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(stubResponse(subId, SubscriptionStatus.ACTIVE));

            service.changePlan(subId, newPlanId);

            verify(eventPublisher).publishDowngraded(any(), eq(oldPlan));
            verify(eventPublisher, never()).publishUpgraded(any(), any());
        }
    }

    // ── cancelSubscription ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelSubscription sets CANCELLED + reason and publishes event")
    void cancelSubscription() {
        UUID subId = UUID.randomUUID();
        SubscriptionPlanEntity planEntity = plan(PLAN_ID, PlanTier.PROFESSIONAL);
        SubscriptionEntity subEntity = sub(subId, SubscriptionStatus.ACTIVE, planEntity);

        when(repository.findById(subId)).thenReturn(Optional.of(subEntity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(stubResponse(subId, SubscriptionStatus.CANCELLED));

        service.cancelSubscription(subId, "Budget cuts");

        assertThat(subEntity.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(subEntity.getCancellationReason()).isEqualTo("Budget cuts");
        assertThat(subEntity.getCancelledAt()).isNotNull();
        verify(eventPublisher).publishCancelled(subEntity);
    }

    // ── suspendSubscription ───────────────────────────────────────────────────

    @Test
    @DisplayName("suspendSubscription sets SUSPENDED and publishes event")
    void suspendSubscription() {
        UUID subId = UUID.randomUUID();
        SubscriptionPlanEntity planEntity = plan(PLAN_ID, PlanTier.PROFESSIONAL);
        SubscriptionEntity subEntity = sub(subId, SubscriptionStatus.ACTIVE, planEntity);

        when(repository.findById(subId)).thenReturn(Optional.of(subEntity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(stubResponse(subId, SubscriptionStatus.SUSPENDED));

        service.suspendSubscription(subId);

        assertThat(subEntity.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        verify(eventPublisher).publishSuspended(subEntity);
    }

    // ── renewSubscription ─────────────────────────────────────────────────────

    @Test
    @DisplayName("renewSubscription sets ACTIVE + new endDate and publishes event")
    void renewSubscription() {
        UUID subId = UUID.randomUUID();
        SubscriptionPlanEntity planEntity = plan(PLAN_ID, PlanTier.PROFESSIONAL);
        SubscriptionEntity subEntity = sub(subId, SubscriptionStatus.EXPIRED, planEntity);
        Instant newEnd = Instant.now().plusSeconds(365L * 24 * 60 * 60);

        when(repository.findById(subId)).thenReturn(Optional.of(subEntity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(stubResponse(subId, SubscriptionStatus.ACTIVE));

        service.renewSubscription(subId, newEnd);

        assertThat(subEntity.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subEntity.getEndDate()).isEqualTo(newEnd);
        verify(eventPublisher).publishRenewed(subEntity);
    }
}
