package com.modus.license.tenant.api;

import com.modus.license.core.domain.enums.TenantStatus;
import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.tenant.api.dto.CreateTenantRequest;
import com.modus.license.tenant.api.dto.TenantResponse;
import com.modus.license.tenant.api.dto.UpdateTenantRequest;
import com.modus.license.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.of(service.createTenant(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(service.getTenant(id));
    }

    @GetMapping("/by-slug/{slug}")
    public ApiResponse<TenantResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.of(service.getTenantBySlug(slug));
    }

    @GetMapping
    public ApiResponse<PageResponse<TenantResponse>> list(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) String region,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<TenantResponse> page;
        if (status != null) {
            page = service.listByStatus(status, pageable);
        } else if (region != null) {
            page = service.listByRegion(region, pageable);
        } else {
            page = service.listTenants(pageable);
        }

        PageResponse<TenantResponse> pageResponse = PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
        return ApiResponse.of(pageResponse);
    }

    @PutMapping("/{id}")
    public ApiResponse<TenantResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateTenantRequest request) {
        return ApiResponse.of(service.updateTenant(id, request));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<TenantResponse> suspend(@PathVariable UUID id) {
        return ApiResponse.of(service.suspendTenant(id));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<TenantResponse> activate(@PathVariable UUID id) {
        return ApiResponse.of(service.activateTenant(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}
