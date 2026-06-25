package com.modus.license.notification.api;

import com.modus.license.notification.api.dto.NotificationResponse;
import com.modus.license.notification.service.NotificationService;
import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Query API for notification records.
 *
 * All endpoints are tenant-scoped and require TENANT_ADMIN or PLATFORM_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/notifications
     *
     * Returns paginated notification history for the current tenant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> search(
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {

        Page<NotificationResponse> page = service.search(pageable);
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.of(page.getContent(), page.getNumber(),
                        page.getSize(), page.getTotalElements())));
    }

    /**
     * GET /api/v1/notifications/{notificationId}
     *
     * Returns a single notification record by ID.
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(
            @PathVariable UUID notificationId) {
        return service.getById(notificationId)
                .map(ApiResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
