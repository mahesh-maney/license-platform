package com.modus.license.reporting.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.reporting.api.dto.EntitlementSnapshotResponse;
import com.modus.license.reporting.api.dto.ExportRequest;
import com.modus.license.reporting.api.dto.SubscriptionSnapshotResponse;
import com.modus.license.reporting.api.dto.UsageMetricResponse;
import com.modus.license.reporting.service.ReportingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Reporting and analytics query API.
 *
 * All endpoints are tenant-scoped and require TENANT_ADMIN or PLATFORM_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService service;

    public ReportingController(ReportingService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/reports/usage
     *
     * Paginated usage metrics for the current tenant, filterable by feature key,
     * metric name, and time window.
     */
    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<PageResponse<UsageMetricResponse>>> getUsageMetrics(
            @RequestParam(required = false) String featureKey,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {

        Page<UsageMetricResponse> page = service.searchUsageMetrics(
                featureKey, metricName, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.of(page.getContent(), page.getNumber(),
                        page.getSize(), page.getTotalElements())));
    }

    /**
     * GET /api/v1/reports/entitlements
     *
     * Paginated entitlement snapshots for the current tenant.
     */
    @GetMapping("/entitlements")
    public ResponseEntity<ApiResponse<PageResponse<EntitlementSnapshotResponse>>> getEntitlements(
            @PageableDefault(size = 50) Pageable pageable) {

        Page<EntitlementSnapshotResponse> page = service.getEntitlementSnapshots(pageable);
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.of(page.getContent(), page.getNumber(),
                        page.getSize(), page.getTotalElements())));
    }

    /**
     * GET /api/v1/reports/subscriptions
     *
     * Paginated subscription snapshots for the current tenant.
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<PageResponse<SubscriptionSnapshotResponse>>> getSubscriptions(
            @PageableDefault(size = 50) Pageable pageable) {

        Page<SubscriptionSnapshotResponse> page = service.getSubscriptionSnapshots(pageable);
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.of(page.getContent(), page.getNumber(),
                        page.getSize(), page.getTotalElements())));
    }

    /**
     * POST /api/v1/reports/export
     *
     * Exports a report to Azure Blob Storage and returns the blob path.
     * Returns 202 Accepted with the blob path, or 503 if export is not configured.
     */
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, String>>> export(
            @RequestBody @Valid ExportRequest request) {

        Optional<String> blobPath = service.exportReport(
                request.reportType(), request.from(), request.to());

        if (blobPath.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.of(Map.of("message",
                            "Report export storage is not configured")));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(Map.of("blobPath", blobPath.get())));
    }
}
