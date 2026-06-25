package com.modus.license.feature.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.feature.api.dto.CreateFeatureRequest;
import com.modus.license.feature.api.dto.FeatureResponse;
import com.modus.license.feature.api.dto.UpdateFeatureRequest;
import com.modus.license.feature.api.mapper.FeatureMapper;
import com.modus.license.feature.domain.entity.FeatureDefinitionEntity;
import com.modus.license.feature.domain.event.FeatureEventPublisher;
import com.modus.license.feature.domain.repository.FeatureDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FeatureDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(FeatureDefinitionService.class);

    private final FeatureDefinitionRepository repository;
    private final FeatureMapper mapper;
    private final FeatureEventPublisher eventPublisher;

    public FeatureDefinitionService(FeatureDefinitionRepository repository,
                                    FeatureMapper mapper,
                                    FeatureEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "FEATURE",
               resourceIdExpression = "#result.featureKey()")
    public FeatureResponse createFeature(CreateFeatureRequest request) {
        if (repository.existsByFeatureKey(request.featureKey())) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "Feature already exists with key: " + request.featureKey());
        }
        FeatureDefinitionEntity entity = mapper.toEntity(request);
        entity = repository.save(entity);

        log.info("Created feature: key={} status={}", entity.getFeatureKey(), entity.getStatus());
        eventPublisher.publishGlobalUpdated(entity);
        return mapper.toResponse(entity);
    }

    public FeatureResponse getFeature(String featureKey) {
        return mapper.toResponse(findOrThrow(featureKey));
    }

    public Page<FeatureResponse> listFeatures(FeatureStatus status, Pageable pageable) {
        if (status != null) {
            return repository.findByStatus(status, pageable).map(mapper::toResponse);
        }
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "FEATURE",
               resourceIdExpression = "#featureKey")
    public FeatureResponse updateFeature(String featureKey, UpdateFeatureRequest request) {
        FeatureDefinitionEntity entity = findOrThrow(featureKey);

        if (request.name() != null)            entity.setName(request.name());
        if (request.description() != null)     entity.setDescription(request.description());
        if (request.minimumPlanTier() != null) entity.setMinimumPlanTier(request.minimumPlanTier());
        if (request.configSchemaJson() != null) entity.setConfigSchemaJson(request.configSchemaJson());

        entity = repository.save(entity);
        log.info("Updated feature: key={}", entity.getFeatureKey());
        eventPublisher.publishGlobalUpdated(entity);
        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.ACTIVATE, resourceType = "FEATURE",
               resourceIdExpression = "#featureKey")
    public FeatureResponse enableFeature(String featureKey) {
        return changeGlobalStatus(featureKey, FeatureStatus.ENABLED,
                (entity, prev) -> eventPublisher.publishGlobalEnabled(entity, prev));
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.DEACTIVATE, resourceType = "FEATURE",
               resourceIdExpression = "#featureKey")
    public FeatureResponse disableFeature(String featureKey) {
        return changeGlobalStatus(featureKey, FeatureStatus.DISABLED,
                (entity, prev) -> eventPublisher.publishGlobalDisabled(entity, prev));
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.EXPIRE, resourceType = "FEATURE",
               resourceIdExpression = "#featureKey")
    public FeatureResponse deprecateFeature(String featureKey) {
        return changeGlobalStatus(featureKey, FeatureStatus.DEPRECATED,
                (entity, prev) -> eventPublisher.publishGlobalDeprecated(entity, prev));
    }

    // -------------------------------------------------------------------------
    // Package-visible — used by TenantFeatureService to resolve definitions
    // -------------------------------------------------------------------------

    FeatureDefinitionEntity findOrThrow(String featureKey) {
        return repository.findByFeatureKey(featureKey)
                .orElseThrow(() -> ResourceNotFoundException.feature(featureKey));
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface StatusChangePublisher {
        void publish(FeatureDefinitionEntity entity, String previousStatus);
    }

    private FeatureResponse changeGlobalStatus(String featureKey, FeatureStatus newStatus,
                                                StatusChangePublisher publisher) {
        FeatureDefinitionEntity entity = findOrThrow(featureKey);
        String previousStatus = entity.getStatus().name();

        entity.setStatus(newStatus);
        entity = repository.save(entity);
        log.info("Changed global feature status: key={} {} -> {}", featureKey, previousStatus, newStatus);
        publisher.publish(entity, previousStatus);
        return mapper.toResponse(entity);
    }
}
