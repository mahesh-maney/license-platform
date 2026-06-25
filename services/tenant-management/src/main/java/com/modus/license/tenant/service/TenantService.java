package com.modus.license.tenant.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.domain.enums.TenantStatus;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.tenant.api.dto.CreateTenantRequest;
import com.modus.license.tenant.api.dto.TenantResponse;
import com.modus.license.tenant.api.dto.UpdateTenantRequest;
import com.modus.license.tenant.api.mapper.TenantMapper;
import com.modus.license.tenant.domain.entity.TenantEntity;
import com.modus.license.tenant.domain.event.TenantEventPublisher;
import com.modus.license.tenant.domain.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository repository;
    private final TenantMapper mapper;
    private final TenantEventPublisher eventPublisher;

    public TenantService(TenantRepository repository,
                         TenantMapper mapper,
                         TenantEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "TENANT",
               resourceIdExpression = "#result.id().toString()")
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (repository.existsBySlug(request.slug())) {
            throw ConflictException.tenantAlreadyExists(request.slug());
        }
        if (repository.existsByAdminEmail(request.adminEmail())) {
            throw new ConflictException(ErrorCode.TENANT_ALREADY_EXISTS,
                    "A tenant already exists with adminEmail: " + request.adminEmail());
        }

        TenantEntity entity = mapper.toEntity(request);

        if (request.planTier() != null && request.planTier().ordinal() >= 1) {
            entity.setStatus(TenantStatus.TRIAL);
            entity.setTrialEndsAt(Instant.now().plusSeconds(30L * 24 * 60 * 60)); // 30-day trial
        } else {
            entity.setStatus(TenantStatus.ACTIVE);
        }

        entity = repository.save(entity);
        log.info("Created tenant: id={} slug={} status={}", entity.getId(), entity.getSlug(), entity.getStatus());

        if (entity.getStatus() == TenantStatus.TRIAL) {
            eventPublisher.publishTrialStarted(entity);
        } else {
            eventPublisher.publishCreated(entity);
        }

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public TenantResponse getTenant(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public TenantResponse getTenantBySlug(String slug) {
        return repository.findBySlug(slug)
                .map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.tenant(slug));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'READONLY')")
    public Page<TenantResponse> listTenants(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'READONLY')")
    public Page<TenantResponse> listByStatus(TenantStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(mapper::toResponse);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'READONLY')")
    public Page<TenantResponse> listByRegion(String region, Pageable pageable) {
        return repository.findByRegion(region, pageable).map(mapper::toResponse);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "TENANT",
               resourceIdExpression = "#id.toString()")
    public TenantResponse updateTenant(UUID id, UpdateTenantRequest request) {
        TenantEntity entity = findOrThrow(id);

        if (request.name() != null)        entity.setName(request.name());
        if (request.displayName() != null) entity.setDisplayName(request.displayName());
        if (request.planTier() != null)    entity.setPlanTier(request.planTier());
        if (request.region() != null)      entity.setRegion(request.region());

        entity = repository.save(entity);
        log.info("Updated tenant: id={}", entity.getId());
        eventPublisher.publishUpdated(entity);

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Lifecycle transitions
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.SUSPEND, resourceType = "TENANT",
               resourceIdExpression = "#id.toString()")
    public TenantResponse suspendTenant(UUID id) {
        TenantEntity entity = findOrThrow(id);
        String previousStatus = entity.getStatus().name();

        entity.setStatus(TenantStatus.SUSPENDED);
        entity = repository.save(entity);

        log.info("Suspended tenant: id={} previousStatus={}", entity.getId(), previousStatus);
        eventPublisher.publishSuspended(entity, previousStatus);

        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.ACTIVATE, resourceType = "TENANT",
               resourceIdExpression = "#id.toString()")
    public TenantResponse activateTenant(UUID id) {
        TenantEntity entity = findOrThrow(id);
        String previousStatus = entity.getStatus().name();

        entity.setStatus(TenantStatus.ACTIVE);
        entity = repository.save(entity);

        log.info("Activated tenant: id={} previousStatus={}", entity.getId(), previousStatus);
        eventPublisher.publishActivated(entity, previousStatus);

        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.DELETE, resourceType = "TENANT",
               resourceIdExpression = "#id.toString()")
    public void deleteTenant(UUID id) {
        TenantEntity entity = findOrThrow(id);
        entity.setStatus(TenantStatus.INACTIVE);
        repository.save(entity);

        log.info("Soft-deleted tenant: id={}", entity.getId());
        eventPublisher.publishDeleted(entity);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private TenantEntity findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.tenant(id.toString()));
    }
}
