package com.modus.license.audit.api;

import com.modus.license.audit.api.dto.AuditLogResponse;
import com.modus.license.audit.service.AuditLogService;
import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Query API for the audit log.
 *
 * All endpoints are tenant-scoped and require TENANT_ADMIN or PLATFORM_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/audit/logs")
public class AuditController {

    private final AuditLogService service;

    public AuditController(AuditLogService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/audit/logs
     *
     * Search audit logs for the current tenant with optional filters.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "recordedAt") Pageable pageable) {

        Page<AuditLogResponse> page = service.search(
                actorId, resourceType, resourceId, outcome, from, to, pageable);

        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.of(page.getContent(), page.getNumber(),
                        page.getSize(), page.getTotalElements())));
    }

    /**
     * GET /api/v1/audit/logs/{auditId}
     *
     * Retrieve a single audit log entry by ID.
     */
    @GetMapping("/{auditId}")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getById(@PathVariable UUID auditId) {
        return service.getById(auditId)
                .map(ApiResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
