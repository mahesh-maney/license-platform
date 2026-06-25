package com.modus.license.reporting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.reporting.api.dto.EntitlementSnapshotResponse;
import com.modus.license.reporting.api.dto.SubscriptionSnapshotResponse;
import com.modus.license.reporting.api.dto.UsageMetricResponse;
import com.modus.license.reporting.api.mapper.ReportingMapper;
import com.modus.license.reporting.domain.repository.EntitlementSnapshotRepository;
import com.modus.license.reporting.domain.repository.SubscriptionSnapshotRepository;
import com.modus.license.reporting.domain.repository.UsageMetricRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReportingService {

    private final UsageMetricRepository         usageRepo;
    private final EntitlementSnapshotRepository entitlementRepo;
    private final SubscriptionSnapshotRepository subscriptionRepo;
    private final ReportingMapper               mapper;
    private final Optional<ReportExportService> exportService;
    private final ObjectMapper                  objectMapper;

    public ReportingService(UsageMetricRepository usageRepo,
                             EntitlementSnapshotRepository entitlementRepo,
                             SubscriptionSnapshotRepository subscriptionRepo,
                             ReportingMapper mapper,
                             Optional<ReportExportService> exportService) {
        this.usageRepo        = usageRepo;
        this.entitlementRepo  = entitlementRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.mapper           = mapper;
        this.exportService    = exportService;
        this.objectMapper     = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Page<UsageMetricResponse> searchUsageMetrics(String featureKey, String metricName,
                                                         Instant from, Instant to,
                                                         Pageable pageable) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return usageRepo.search(tenantId, featureKey, metricName, from, to, pageable)
                .map(mapper::toUsageResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Page<EntitlementSnapshotResponse> getEntitlementSnapshots(Pageable pageable) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return entitlementRepo.findByTenantIdOrderByRecordedAtDesc(tenantId, pageable)
                .map(mapper::toEntitlementResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Page<SubscriptionSnapshotResponse> getSubscriptionSnapshots(Pageable pageable) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return subscriptionRepo.findByTenantIdOrderByRecordedAtDesc(tenantId, pageable)
                .map(mapper::toSubscriptionResponse);
    }

    /**
     * Exports the requested report type to Azure Blob Storage.
     *
     * @return blob path, or {@code null} if export storage is not configured
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Optional<String> exportReport(String reportType, Instant from, Instant to) {
        if (exportService.isEmpty()) {
            return Optional.empty();
        }
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        try {
            String json = buildExportJson(reportType, tenantId, from, to);
            String blobPath = exportService.get().export(tenantId, reportType, json);
            return Optional.of(blobPath);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise report data", e);
        }
    }

    private String buildExportJson(String reportType, UUID tenantId,
                                    Instant from, Instant to) throws JsonProcessingException {
        Object data = switch (reportType.toUpperCase()) {
            case "USAGE" -> usageRepo
                    .findByTenantIdAndWindowStartGreaterThanEqualAndWindowEndLessThanEqual(
                            tenantId,
                            from != null ? from : Instant.EPOCH,
                            to   != null ? to   : Instant.now())
                    .stream().map(mapper::toUsageResponse).toList();
            case "ENTITLEMENT" -> entitlementRepo.findByTenantId(tenantId)
                    .stream().map(mapper::toEntitlementResponse).toList();
            case "SUBSCRIPTION" -> subscriptionRepo.findByTenantId(tenantId)
                    .stream().map(mapper::toSubscriptionResponse).toList();
            default -> throw new IllegalArgumentException("Unknown report type: " + reportType);
        };
        return objectMapper.writeValueAsString(data);
    }
}
