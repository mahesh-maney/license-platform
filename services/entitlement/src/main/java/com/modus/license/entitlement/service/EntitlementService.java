package com.modus.license.entitlement.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ErrorCode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    private static final List<EntitlementStatus> ACTIVE_STATUSES =
            List.of(EntitlementStatus.ACTIVE, EntitlementStatus.PENDING);

    private final EntitlementRepository repository;
    private final EntitlementMapper mapper;
    private final EntitlementEventPublisher eventPublisher;
    private final EntitlementCacheService cacheService;

    public EntitlementService(EntitlementRepository repository,
                              EntitlementMapper mapper,
                              EntitlementEventPublisher eventPublisher,
                              EntitlementCacheService cacheService) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
    }

    // =========================================================================
    // Public API — require authentication
    // =========================================================================

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "ENTITLEMENT",
               resourceIdExpression = "#result.id().toString()")
    public EntitlementResponse grantEntitlement(CreateEntitlementRequest request) {
        EntitlementEntity entity = mapper.toEntity(request);
        entity.setStatus(EntitlementStatus.ACTIVE);
        entity = repository.save(entity);

        log.info("Granted entitlement: id={} tenantId={}", entity.getId(), entity.getTenantId());
        eventPublisher.publishGranted(entity);

        EntitlementResponse response = mapper.toResponse(entity);
        cacheService.cache(entity.getTenantId(), response);
        return response;
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public EntitlementResponse getEntitlement(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY', 'SERVICE_ACCOUNT')")
    public EntitlementResponse getActiveEntitlement() {
        UUID tenantId = TenantContextHolder.require().tenantId().value();

        return cacheService.get(tenantId).orElseGet(() -> {
            EntitlementEntity entity = repository
                    .findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, ACTIVE_STATUSES)
                    .orElseThrow(() -> new ModusException(ErrorCode.ENTITLEMENT_NOT_FOUND,
                            "No active entitlement found for tenant: " + tenantId));
            EntitlementResponse response = mapper.toResponse(entity);
            cacheService.cache(tenantId, response);
            return response;
        });
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public Page<EntitlementResponse> listEntitlements(Pageable pageable) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return repository.findByTenantId(tenantId, pageable).map(mapper::toResponse);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "ENTITLEMENT",
               resourceIdExpression = "#id.toString()")
    public EntitlementResponse updateEntitlement(UUID id, UpdateEntitlementRequest request) {
        EntitlementEntity entity = findOrThrow(id);

        if (request.featureKeys() != null) {
            entity.getFeatureKeys().clear();
            entity.getFeatureKeys().addAll(request.featureKeys());
        }
        if (request.seatLimit() != null) entity.setSeatLimit(request.seatLimit());
        if (request.endDate() != null)   entity.setEndDate(request.endDate());

        entity = repository.save(entity);
        log.info("Updated entitlement: id={}", entity.getId());

        EntitlementResponse response = mapper.toResponse(entity);
        cacheService.cache(entity.getTenantId(), response);
        eventPublisher.publishUpdated(entity);
        return response;
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.REVOKE, resourceType = "ENTITLEMENT",
               resourceIdExpression = "#id.toString()")
    public EntitlementResponse revokeEntitlement(UUID id) {
        EntitlementEntity entity = findOrThrow(id);
        String previousStatus = entity.getStatus().name();
        entity.setStatus(EntitlementStatus.REVOKED);
        entity = repository.save(entity);

        log.info("Revoked entitlement: id={} tenantId={} previousStatus={}", entity.getId(), entity.getTenantId(), previousStatus);
        cacheService.evict(entity.getTenantId());
        eventPublisher.publishRevoked(entity, previousStatus);
        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.SUSPEND, resourceType = "ENTITLEMENT",
               resourceIdExpression = "#id.toString()")
    public EntitlementResponse suspendEntitlement(UUID id) {
        EntitlementEntity entity = findOrThrow(id);
        String previousStatus = entity.getStatus().name();
        entity.setStatus(EntitlementStatus.SUSPENDED);
        entity = repository.save(entity);

        log.info("Suspended entitlement: id={} tenantId={} previousStatus={}", entity.getId(), entity.getTenantId(), previousStatus);
        cacheService.evict(entity.getTenantId());
        eventPublisher.publishSuspended(entity, previousStatus);
        return mapper.toResponse(entity);
    }

    // =========================================================================
    // Internal lifecycle — called by Kafka consumers (no @PreAuthorize)
    // =========================================================================

    @Transactional
    public void processSubscriptionCreated(UUID tenantId, UUID subscriptionId, UUID planId,
                                           PlanTier planTier, LicenseType licenseType,
                                           Integer seatLimit, Instant startDate, Instant endDate) {
        EntitlementEntity entity = new EntitlementEntity();
        entity.setTenantId(tenantId);
        entity.setSubscriptionId(subscriptionId);
        entity.setPlanId(planId);
        entity.setPlanTier(planTier);
        entity.setLicenseType(licenseType);
        entity.setSeatLimit(seatLimit);
        entity.setStatus(EntitlementStatus.ACTIVE);
        entity.setStartDate(startDate != null ? startDate : Instant.now());
        entity.setEndDate(endDate);

        entity = repository.save(entity);
        log.info("Auto-granted entitlement: id={} tenantId={} from subscriptionId={}",
                entity.getId(), tenantId, subscriptionId);

        eventPublisher.publishGranted(entity);
        cacheService.cache(tenantId, mapper.toResponse(entity));
    }

    @Transactional
    public void processSubscriptionPlanChanged(UUID subscriptionId, UUID newPlanId,
                                               PlanTier newPlanTier, LicenseType newLicenseType,
                                               Integer newSeatLimit) {
        repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).ifPresent(entity -> {
            entity.setPlanId(newPlanId);
            entity.setPlanTier(newPlanTier);
            entity.setLicenseType(newLicenseType);
            entity.setSeatLimit(newSeatLimit);
            entity = repository.save(entity);
            log.info("Updated entitlement plan: id={} subscriptionId={} newPlanId={}", entity.getId(), subscriptionId, newPlanId);
            cacheService.cache(entity.getTenantId(), mapper.toResponse(entity));
            eventPublisher.publishUpdated(entity);
        });
    }

    @Transactional
    public void processSubscriptionStatusChanged(UUID subscriptionId, EntitlementStatus newStatus) {
        repository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).ifPresent(entity -> {
            String previousStatus = entity.getStatus().name();
            entity.setStatus(newStatus);
            entity = repository.save(entity);
            log.info("Entitlement status changed: id={} subscriptionId={} {} -> {}", entity.getId(), subscriptionId, previousStatus, newStatus);
            cacheService.evict(entity.getTenantId());

            switch (newStatus) {
                case REVOKED   -> eventPublisher.publishRevoked(entity, previousStatus);
                case SUSPENDED -> eventPublisher.publishSuspended(entity, previousStatus);
                case EXPIRED   -> eventPublisher.publishExpired(entity);
                case ACTIVE    -> eventPublisher.publishRenewed(entity);
                default        -> eventPublisher.publishUpdated(entity);
            }
        });
    }

    @Transactional
    public void processTenantSuspended(UUID tenantId) {
        repository.findByTenantIdAndStatus(tenantId, EntitlementStatus.ACTIVE)
                .forEach(entity -> {
                    entity.setStatus(EntitlementStatus.SUSPENDED);
                    entity = repository.save(entity);
                    eventPublisher.publishSuspended(entity, EntitlementStatus.ACTIVE.name());
                });
        log.info("Suspended all active entitlements for tenant: tenantId={}", tenantId);
        cacheService.evict(tenantId);
    }

    @Transactional
    public void processTenantActivated(UUID tenantId) {
        repository.findByTenantIdAndStatus(tenantId, EntitlementStatus.SUSPENDED)
                .forEach(entity -> {
                    entity.setStatus(EntitlementStatus.ACTIVE);
                    entity = repository.save(entity);
                    eventPublisher.publishRenewed(entity);
                    cacheService.cache(tenantId, mapper.toResponse(entity));
                });
        log.info("Re-activated suspended entitlements for tenant: tenantId={}", tenantId);
    }

    @Transactional
    public void processFeatureEnabled(UUID tenantId, String featureKey) {
        repository.findByTenantIdAndStatus(tenantId, EntitlementStatus.ACTIVE)
                .forEach(entity -> {
                    if (entity.getFeatureKeys().add(featureKey)) {
                        entity = repository.save(entity);
                        cacheService.cache(tenantId, mapper.toResponse(entity));
                        eventPublisher.publishUpdated(entity);
                    }
                });
        log.info("Feature enabled on entitlements: tenantId={} featureKey={}", tenantId, featureKey);
    }

    @Transactional
    public void processFeatureDisabled(UUID tenantId, String featureKey) {
        repository.findByTenantIdAndStatus(tenantId, EntitlementStatus.ACTIVE)
                .forEach(entity -> {
                    if (entity.getFeatureKeys().remove(featureKey)) {
                        entity = repository.save(entity);
                        cacheService.cache(tenantId, mapper.toResponse(entity));
                        eventPublisher.publishUpdated(entity);
                    }
                });
        log.info("Feature disabled on entitlements: tenantId={} featureKey={}", tenantId, featureKey);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private EntitlementEntity findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ENTITLEMENT_NOT_FOUND,
                        "Entitlement not found: " + id));
    }
}
