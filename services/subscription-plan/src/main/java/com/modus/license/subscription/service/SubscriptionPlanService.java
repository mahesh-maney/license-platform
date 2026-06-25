package com.modus.license.subscription.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.subscription.api.dto.CreatePlanRequest;
import com.modus.license.subscription.api.dto.PlanResponse;
import com.modus.license.subscription.api.dto.UpdatePlanRequest;
import com.modus.license.subscription.api.mapper.SubscriptionPlanMapper;
import com.modus.license.subscription.domain.entity.SubscriptionPlanEntity;
import com.modus.license.subscription.domain.repository.SubscriptionPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubscriptionPlanService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanService.class);

    private final SubscriptionPlanRepository repository;
    private final SubscriptionPlanMapper mapper;

    public SubscriptionPlanService(SubscriptionPlanRepository repository,
                                   SubscriptionPlanMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "SUBSCRIPTION_PLAN",
               resourceIdExpression = "#result.id().toString()")
    public PlanResponse createPlan(CreatePlanRequest request) {
        if (repository.existsByName(request.name())) {
            throw new ConflictException(ErrorCode.PLAN_ALREADY_EXISTS,
                    "A subscription plan with name '" + request.name() + "' already exists");
        }

        SubscriptionPlanEntity entity = mapper.toEntity(request);
        entity = repository.save(entity);

        log.info("Created subscription plan: id={} name={} tier={}", entity.getId(), entity.getName(), entity.getTier());
        return mapper.toResponse(entity);
    }

    public PlanResponse getPlan(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    public Page<PlanResponse> listPlans(Boolean activeOnly, Pageable pageable) {
        if (Boolean.TRUE.equals(activeOnly)) {
            return repository.findByActive(true, pageable).map(mapper::toResponse);
        }
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    public List<PlanResponse> listByTier(PlanTier tier) {
        return repository.findByTierAndActive(tier, true)
                .stream().map(mapper::toResponse).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "SUBSCRIPTION_PLAN",
               resourceIdExpression = "#id.toString()")
    public PlanResponse updatePlan(UUID id, UpdatePlanRequest request) {
        SubscriptionPlanEntity entity = findOrThrow(id);

        if (request.description() != null) entity.setDescription(request.description());
        if (request.maxSeats() != null)    entity.setMaxSeats(request.maxSeats());
        if (request.priceInCents() != null) entity.setPriceInCents(request.priceInCents());
        if (request.active() != null)      entity.setActive(request.active());
        if (request.features() != null) {
            entity.getFeatures().clear();
            entity.getFeatures().addAll(request.features());
        }

        entity = repository.save(entity);
        log.info("Updated subscription plan: id={}", entity.getId());
        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.DEACTIVATE, resourceType = "SUBSCRIPTION_PLAN",
               resourceIdExpression = "#id.toString()")
    public PlanResponse deactivatePlan(UUID id) {
        SubscriptionPlanEntity entity = findOrThrow(id);
        entity.setActive(false);
        entity = repository.save(entity);
        log.info("Deactivated subscription plan: id={}", entity.getId());
        return mapper.toResponse(entity);
    }

    public SubscriptionPlanEntity findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.plan(id.toString()));
    }
}
