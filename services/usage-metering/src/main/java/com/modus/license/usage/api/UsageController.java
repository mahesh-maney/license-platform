package com.modus.license.usage.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.usage.api.dto.RecordUsageRequest;
import com.modus.license.usage.api.dto.UsageResponse;
import com.modus.license.usage.service.UsageMeteringService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for direct usage recording by SDK clients or internal services.
 *
 * Kafka consumers (SessionEventConsumer, EnforcementEventConsumer) provide
 * the automatic recording path; this endpoint is for explicit SDK calls.
 */
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final UsageMeteringService service;

    public UsageController(UsageMeteringService service) {
        this.service = service;
    }

    /**
     * POST /api/v1/usage/record
     *
     * Record a single usage event for the authenticated tenant.
     * Returns the usage record as confirmation; persistence is async via Kafka.
     */
    @PostMapping("/record")
    public ResponseEntity<ApiResponse<UsageResponse>> recordUsage(
            @Valid @RequestBody RecordUsageRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(service.recordUsage(request)));
    }
}
