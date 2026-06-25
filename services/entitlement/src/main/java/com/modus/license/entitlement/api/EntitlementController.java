package com.modus.license.entitlement.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.entitlement.api.dto.CreateEntitlementRequest;
import com.modus.license.entitlement.api.dto.EntitlementResponse;
import com.modus.license.entitlement.api.dto.UpdateEntitlementRequest;
import com.modus.license.entitlement.service.EntitlementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/entitlements")
public class EntitlementController {

    private final EntitlementService service;

    public EntitlementController(EntitlementService service) {
        this.service = service;
    }

    /** Manual grant — PLATFORM_ADMIN only. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EntitlementResponse> grant(@Valid @RequestBody CreateEntitlementRequest request) {
        return ApiResponse.of(service.grantEntitlement(request));
    }

    /** Active entitlement for the calling tenant (cache-first). */
    @GetMapping("/active")
    public ApiResponse<EntitlementResponse> getActive() {
        return ApiResponse.of(service.getActiveEntitlement());
    }

    @GetMapping("/{id}")
    public ApiResponse<EntitlementResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(service.getEntitlement(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<EntitlementResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<EntitlementResponse> page = service.listEntitlements(pageable);
        return ApiResponse.of(PageResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements()));
    }

    @PutMapping("/{id}")
    public ApiResponse<EntitlementResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateEntitlementRequest request) {
        return ApiResponse.of(service.updateEntitlement(id, request));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<EntitlementResponse> revoke(@PathVariable UUID id) {
        return ApiResponse.of(service.revokeEntitlement(id));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<EntitlementResponse> suspend(@PathVariable UUID id) {
        return ApiResponse.of(service.suspendEntitlement(id));
    }
}
