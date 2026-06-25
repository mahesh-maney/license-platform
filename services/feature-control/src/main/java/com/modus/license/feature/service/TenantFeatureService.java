package com.modus.license.feature.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.exception.ForbiddenException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ModusException;
import com.modus.license.feature.api.dto.SetTenantFeatureRequest;
import com.modus.license.feature.api.dto.TenantFeatureResponse;
import com.modus.license.feature.domain.entity.FeatureDefinitionEntity;
import com.modus.license.feature.domain.entity.TenantFeatureOverrideEntity;
import com.modus.license.feature.domain.event.FeatureEventPublisher;
import com.modus.license.feature.domain.repository.FeatureDefinitionRepository;
import com.modus.license.feature.domain.repository.TenantFeatureOverrideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TenantFeatureService {

    private static final Logger log = LoggerFactory.getLogger(TenantFeatureService.class);

    private final FeatureDefinitionRepository featureRepository;
    private final TenantFeatureOverrideRepository overrideRepository;
    private final FeatureDefinitionService definitionService;
    private final FeatureEventPublisher eventPublisher;

    public TenantFeatureService(FeatureDefinitionRepository featureRepository,
                                TenantFeatureOverrideRepository overrideRepository,
                                FeatureDefinitionService definitionService,
                                FeatureEventPublisher eventPublisher) {
        this.featureRepository = featureRepository;
        this.overrideRepository = overrideRepository;
        this.definitionService = definitionService;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public List<TenantFeatureResponse> listEffectiveFeatures() {
        UUID tenantId = currentTenantId();
        return listEffectiveFeaturesForTenant(tenantId);
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<TenantFeatureResponse> listEffectiveFeaturesForTenant(UUID tenantId) {
        List<FeatureDefinitionEntity> allFeatures = featureRepository.findAll();
        Map<String, TenantFeatureOverrideEntity> overridesByKey =
                overrideRepository.findByTenantId(tenantId).stream()
                        .collect(Collectors.toMap(TenantFeatureOverrideEntity::getFeatureKey,
                                Function.identity()));

        return allFeatures.stream()
                .map(f -> toTenantResponse(f, tenantId, overridesByKey.get(f.getFeatureKey())))
                .toList();
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public TenantFeatureResponse getEffectiveFeature(String featureKey) {
        UUID tenantId = currentTenantId();
        FeatureDefinitionEntity feature = definitionService.findOrThrow(featureKey);
        Optional<TenantFeatureOverrideEntity> override =
                overrideRepository.findByTenantIdAndFeatureKey(tenantId, featureKey);
        return toTenantResponse(feature, tenantId, override.orElse(null));
    }

    // -------------------------------------------------------------------------
    // Override management
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "TENANT_FEATURE",
               resourceIdExpression = "#featureKey")
    public TenantFeatureResponse setOverride(String featureKey, SetTenantFeatureRequest request) {
        UUID tenantId = currentTenantId();
        return setOverrideForTenant(tenantId, featureKey, request);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "TENANT_FEATURE",
               resourceIdExpression = "#featureKey")
    public TenantFeatureResponse setOverrideForTenant(UUID tenantId, String featureKey,
                                                      SetTenantFeatureRequest request) {
        // TENANT_ADMIN cannot set BETA — that is PLATFORM_ADMIN-only
        FeatureDefinitionEntity feature = definitionService.findOrThrow(featureKey);
        String previousStatus = null;

        TenantFeatureOverrideEntity override =
                overrideRepository.findByTenantIdAndFeatureKey(tenantId, featureKey)
                        .orElseGet(() -> {
                            TenantFeatureOverrideEntity o = new TenantFeatureOverrideEntity();
                            o.setTenantId(tenantId);
                            o.setFeature(feature);
                            o.setFeatureKey(featureKey);
                            return o;
                        });

        if (override.getStatus() != null) {
            previousStatus = override.getStatus().name();
        }
        override.setStatus(request.status());
        override.setConfigJson(request.configJson());
        overrideRepository.save(override);

        log.info("Set tenant feature override: tenantId={} key={} status={}", tenantId, featureKey, request.status());
        publishOverrideEvent(feature, tenantId, request.status(), previousStatus, request.configJson());

        return toTenantResponse(feature, tenantId, override);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.DELETE, resourceType = "TENANT_FEATURE",
               resourceIdExpression = "#featureKey")
    public void removeOverride(String featureKey) {
        UUID tenantId = currentTenantId();
        removeOverrideForTenant(tenantId, featureKey);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void removeOverrideForTenant(UUID tenantId, String featureKey) {
        FeatureDefinitionEntity feature = definitionService.findOrThrow(featureKey);
        overrideRepository.findByTenantIdAndFeatureKey(tenantId, featureKey).ifPresent(o -> {
            String previousStatus = o.getStatus().name();
            overrideRepository.deleteByTenantIdAndFeatureKey(tenantId, featureKey);
            log.info("Removed tenant feature override: tenantId={} key={}", tenantId, featureKey);
            // Revert to global — publish DISABLED if global is not accessible
            eventPublisher.publishTenantDisabled(feature, tenantId, previousStatus);
        });
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private TenantFeatureResponse toTenantResponse(FeatureDefinitionEntity feature,
                                                    UUID tenantId,
                                                    TenantFeatureOverrideEntity override) {
        FeatureStatus globalStatus = feature.getStatus();
        FeatureStatus overriddenStatus = override != null ? override.getStatus() : null;
        FeatureStatus effectiveStatus = overriddenStatus != null ? overriddenStatus : globalStatus;
        String configJson = override != null ? override.getConfigJson() : null;

        return new TenantFeatureResponse(
                tenantId,
                feature.getFeatureKey(),
                feature.getName(),
                feature.getMinimumPlanTier(),
                globalStatus,
                overriddenStatus,
                effectiveStatus,
                configJson,
                effectiveStatus.isAccessible()
        );
    }

    private void publishOverrideEvent(FeatureDefinitionEntity feature, UUID tenantId,
                                       FeatureStatus newStatus, String previousStatus, String configJson) {
        switch (newStatus) {
            case ENABLED   -> eventPublisher.publishTenantEnabled(feature, tenantId, previousStatus, configJson);
            case DISABLED  -> eventPublisher.publishTenantDisabled(feature, tenantId, previousStatus);
            case BETA      -> eventPublisher.publishBetaGranted(feature, tenantId);
            case DEPRECATED -> eventPublisher.publishGlobalDeprecated(feature, previousStatus);
        }
    }

    private UUID currentTenantId() {
        return TenantContextHolder.require().tenantId().value();
    }
}
