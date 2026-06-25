package com.modus.license.feature.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.feature.api.dto.SetTenantFeatureRequest;
import com.modus.license.feature.api.dto.TenantFeatureResponse;
import com.modus.license.feature.service.TenantFeatureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant-features")
public class TenantFeatureController {

    private final TenantFeatureService service;

    public TenantFeatureController(TenantFeatureService service) {
        this.service = service;
    }

    /** Effective features for the calling tenant (context-scoped). */
    @GetMapping
    public ApiResponse<List<TenantFeatureResponse>> listOwn() {
        return ApiResponse.of(service.listEffectiveFeatures());
    }

    /** Effective status of one feature for the calling tenant. */
    @GetMapping("/{featureKey}")
    public ApiResponse<TenantFeatureResponse> getOwn(@PathVariable String featureKey) {
        return ApiResponse.of(service.getEffectiveFeature(featureKey));
    }

    /** Set or update a feature override for the calling tenant. */
    @PutMapping("/{featureKey}")
    public ApiResponse<TenantFeatureResponse> setOverride(@PathVariable String featureKey,
                                                          @Valid @RequestBody SetTenantFeatureRequest request) {
        return ApiResponse.of(service.setOverride(featureKey, request));
    }

    /** Remove a feature override (reverts to global default). */
    @DeleteMapping("/{featureKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOverride(@PathVariable String featureKey) {
        service.removeOverride(featureKey);
    }

    // -------------------------------------------------------------------------
    // PLATFORM_ADMIN — manage any tenant's features
    // -------------------------------------------------------------------------

    @GetMapping("/admin/{tenantId}")
    public ApiResponse<List<TenantFeatureResponse>> listForTenant(@PathVariable UUID tenantId) {
        return ApiResponse.of(service.listEffectiveFeaturesForTenant(tenantId));
    }

    @PutMapping("/admin/{tenantId}/{featureKey}")
    public ApiResponse<TenantFeatureResponse> setOverrideForTenant(@PathVariable UUID tenantId,
                                                                    @PathVariable String featureKey,
                                                                    @Valid @RequestBody SetTenantFeatureRequest request) {
        return ApiResponse.of(service.setOverrideForTenant(tenantId, featureKey, request));
    }

    @DeleteMapping("/admin/{tenantId}/{featureKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOverrideForTenant(@PathVariable UUID tenantId, @PathVariable String featureKey) {
        service.removeOverrideForTenant(tenantId, featureKey);
    }
}
