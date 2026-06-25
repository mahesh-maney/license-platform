package com.modus.license.subscription.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.subscription.api.dto.CreateSubscriptionRequest;
import com.modus.license.subscription.api.dto.SubscriptionResponse;
import com.modus.license.subscription.api.mapper.SubscriptionMapper;
import com.modus.license.subscription.domain.entity.SubscriptionEntity;
import com.modus.license.subscription.domain.entity.SubscriptionPlanEntity;
import com.modus.license.subscription.domain.enums.SubscriptionStatus;
import com.modus.license.subscription.domain.event.SubscriptionEventPublisher;
import com.modus.license.subscription.domain.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private static final List<SubscriptionStatus> ACTIVE_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL);

    private final SubscriptionRepository repository;
    private final SubscriptionMapper mapper;
    private final SubscriptionPlanService planService;
    private final SubscriptionEventPublisher eventPublisher;

    public SubscriptionService(SubscriptionRepository repository,
                               SubscriptionMapper mapper,
                               SubscriptionPlanService planService,
                               SubscriptionEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.planService = planService;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BILLING_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "SUBSCRIPTION",
               resourceIdExpression = "#result.id().toString()")
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        UUID tenantId = currentTenantId();

        if (repository.existsByTenantIdAndStatusIn(tenantId, ACTIVE_STATUSES)) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "Tenant already has an active or trial subscription. Cancel or upgrade instead.");
        }

        SubscriptionPlanEntity plan = planService.findOrThrow(request.planId());

        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setTenantId(tenantId);
        entity.setPlan(plan);
        entity.setStatus(SubscriptionStatus.ACTIVE);
        entity.setSeatLimit(request.seatLimit());
        entity.setBillingCycle(request.billingCycle());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());

        entity = repository.save(entity);
        log.info("Created subscription: id={} tenantId={} plan={}", entity.getId(), tenantId, plan.getName());
        eventPublisher.publishCreated(entity);

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BILLING_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public SubscriptionResponse getSubscription(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BILLING_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public SubscriptionResponse getActiveSubscription() {
        UUID tenantId = currentTenantId();
        return repository.findByTenantIdAndStatusIn(tenantId, ACTIVE_STATUSES)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ModusException(ErrorCode.CONFLICT,
                        "No active subscription found for tenant"));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BILLING_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public Page<SubscriptionResponse> listSubscriptions(Pageable pageable) {
        UUID tenantId = currentTenantId();
        return repository.findByTenantId(tenantId, pageable).map(mapper::toResponse);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'READONLY')")
    public Page<SubscriptionResponse> listByStatus(SubscriptionStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(mapper::toResponse);
    }

    // -------------------------------------------------------------------------
    // Upgrade / Downgrade
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BILLING_ADMIN')")
    @Auditable(action = AuditAction.UPGRADE, resourceType = "SUBSCRIPTION",
               resourceIdExpression = "#id.toString()")
    public SubscriptionResponse changePlan(UUID id, UUID newPlanId) {
        SubscriptionEntity entity = findOrThrow(id);
        SubscriptionPlanEntity previousPlan = entity.getPlan();
        SubscriptionPlanEntity newPlan = planService.findOrThrow(newPlanId);

        boolean isUpgrade = newPlan.getTier().isAtLeast(previousPlan.getTier())
                && !newPlan.getId().equals(previousPlan.getId());

        entity.setPlan(newPlan);
        entity = repository.save(entity);

        log.info("Changed plan for subscription: id={} from={} to={}", id, previousPlan.getName(), newPlan.getName());

        if (isUpgrade) {
            eventPublisher.publishUpgraded(entity, previousPlan);
        } else {
            eventPublisher.publishDowngraded(entity, previousPlan);
        }

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BILLING_ADMIN')")
    @Auditable(action = AuditAction.CANCEL, resourceType = "SUBSCRIPTION",
               resourceIdExpression = "#id.toString()")
    public SubscriptionResponse cancelSubscription(UUID id, String reason) {
        SubscriptionEntity entity = findOrThrow(id);
        entity.setStatus(SubscriptionStatus.CANCELLED);
        entity.setCancellationReason(reason);
        entity.setCancelledAt(Instant.now());
        entity = repository.save(entity);

        log.info("Cancelled subscription: id={}", entity.getId());
        eventPublisher.publishCancelled(entity);

        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.SUSPEND, resourceType = "SUBSCRIPTION",
               resourceIdExpression = "#id.toString()")
    public SubscriptionResponse suspendSubscription(UUID id) {
        SubscriptionEntity entity = findOrThrow(id);
        entity.setStatus(SubscriptionStatus.SUSPENDED);
        entity = repository.save(entity);

        log.info("Suspended subscription: id={}", entity.getId());
        eventPublisher.publishSuspended(entity);

        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BILLING_ADMIN')")
    @Auditable(action = AuditAction.RENEW, resourceType = "SUBSCRIPTION",
               resourceIdExpression = "#id.toString()")
    public SubscriptionResponse renewSubscription(UUID id, Instant newEndDate) {
        SubscriptionEntity entity = findOrThrow(id);
        entity.setStatus(SubscriptionStatus.ACTIVE);
        entity.setEndDate(newEndDate);
        entity = repository.save(entity);

        log.info("Renewed subscription: id={}", entity.getId());
        eventPublisher.publishRenewed(entity);

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private SubscriptionEntity findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND,
                        "Subscription not found: " + id));
    }

    private UUID currentTenantId() {
        return TenantContextHolder.require().tenantId().value();
    }
}
