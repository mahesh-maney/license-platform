package com.modus.license.reporting.service;

import com.modus.license.reporting.api.dto.EntitlementSnapshotResponse;
import com.modus.license.reporting.api.dto.SubscriptionSnapshotResponse;
import com.modus.license.reporting.api.dto.UsageMetricResponse;
import com.modus.license.reporting.api.mapper.ReportingMapper;
import com.modus.license.reporting.domain.entity.EntitlementSnapshotEntity;
import com.modus.license.reporting.domain.entity.SubscriptionSnapshotEntity;
import com.modus.license.reporting.domain.entity.UsageMetricEntity;
import com.modus.license.reporting.domain.repository.EntitlementSnapshotRepository;
import com.modus.license.reporting.domain.repository.SubscriptionSnapshotRepository;
import com.modus.license.reporting.domain.repository.UsageMetricRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingService")
class ReportingServiceTest {

    @Mock UsageMetricRepository          usageRepo;
    @Mock EntitlementSnapshotRepository  entitlementRepo;
    @Mock SubscriptionSnapshotRepository subscriptionRepo;
    @Mock ReportingMapper                mapper;
    @Mock ReportExportService            exportService;

    ReportingService service;
    ReportingService serviceWithoutExport;

    static final UUID   TENANT_ID = TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final Instant NOW       = Instant.now();

    @BeforeEach
    void setUp() {
        service              = new ReportingService(usageRepo, entitlementRepo, subscriptionRepo,
                mapper, Optional.of(exportService));
        serviceWithoutExport = new ReportingService(usageRepo, entitlementRepo, subscriptionRepo,
                mapper, Optional.empty());
        TenantContextTestHelper.setDefault();
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsageMetricResponse usageResponse() {
        return new UsageMetricResponse(UUID.randomUUID(), TENANT_ID,
                "EXPORT_PDF", "API_CALLS", "CALLS",
                NOW.minusSeconds(3600), NOW, 42.0, 10L, 0);
    }

    private EntitlementSnapshotResponse entitlementResponse() {
        return new EntitlementSnapshotResponse(UUID.randomUUID(), TENANT_ID,
                UUID.randomUUID(), UUID.randomUUID(), "PROFESSIONAL",
                "CONCURRENT", null, List.of("FEATURE_A"), "ACTIVE",
                NOW.minusSeconds(86400), null, NOW);
    }

    private SubscriptionSnapshotResponse subscriptionResponse() {
        return new SubscriptionSnapshotResponse(UUID.randomUUID(), TENANT_ID,
                UUID.randomUUID(), "PROFESSIONAL", "CONCURRENT",
                "MONTHLY", null, "ACTIVE",
                NOW.minusSeconds(86400), null, NOW);
    }

    // ── searchUsageMetrics ────────────────────────────────────────────────────

    @Test
    @DisplayName("searchUsageMetrics delegates to repo.search with tenant context")
    void searchUsageMetrics() {
        UsageMetricEntity entity = new UsageMetricEntity();
        when(usageRepo.search(eq(TENANT_ID), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toUsageResponse(entity)).thenReturn(usageResponse());

        var result = service.searchUsageMetrics(null, null, null, null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        verify(usageRepo).search(eq(TENANT_ID), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("searchUsageMetrics passes featureKey and metricName filters to repo")
    void searchUsageMetrics_withFilters() {
        when(usageRepo.search(eq(TENANT_ID), eq("EXPORT_PDF"), eq("API_CALLS"), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.searchUsageMetrics("EXPORT_PDF", "API_CALLS", null, null, Pageable.unpaged());

        assertThat(result.getContent()).isEmpty();
        verify(usageRepo).search(eq(TENANT_ID), eq("EXPORT_PDF"), eq("API_CALLS"), isNull(), isNull(), any());
    }

    // ── getEntitlementSnapshots ───────────────────────────────────────────────

    @Test
    @DisplayName("getEntitlementSnapshots delegates to repo with tenant context")
    void getEntitlementSnapshots() {
        EntitlementSnapshotEntity entity = new EntitlementSnapshotEntity();
        when(entitlementRepo.findByTenantIdOrderByRecordedAtDesc(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toEntitlementResponse(entity)).thenReturn(entitlementResponse());

        var result = service.getEntitlementSnapshots(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    // ── getSubscriptionSnapshots ──────────────────────────────────────────────

    @Test
    @DisplayName("getSubscriptionSnapshots delegates to repo with tenant context")
    void getSubscriptionSnapshots() {
        SubscriptionSnapshotEntity entity = new SubscriptionSnapshotEntity();
        when(subscriptionRepo.findByTenantIdOrderByRecordedAtDesc(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toSubscriptionResponse(entity)).thenReturn(subscriptionResponse());

        var result = service.getSubscriptionSnapshots(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    // ── exportReport ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("exportReport returns Optional.empty when no ReportExportService configured")
    void exportReport_noExportService() {
        Optional<String> result = serviceWithoutExport.exportReport("USAGE", null, null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("exportReport delegates to ReportExportService and returns blob path")
    void exportReport_withExportService() {
        when(usageRepo.findByTenantIdAndWindowStartGreaterThanEqualAndWindowEndLessThanEqual(
                eq(TENANT_ID), any(), any()))
                .thenReturn(List.of());
        when(exportService.export(eq(TENANT_ID), eq("USAGE"), any()))
                .thenReturn("reports/path/usage.json");

        Optional<String> result = service.exportReport("USAGE", null, null);

        assertThat(result).contains("reports/path/usage.json");
    }
}
