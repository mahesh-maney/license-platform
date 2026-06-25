package com.modus.license.feature.api;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.feature.api.dto.CreateFeatureRequest;
import com.modus.license.feature.api.dto.FeatureResponse;
import com.modus.license.feature.api.dto.UpdateFeatureRequest;
import com.modus.license.feature.service.FeatureDefinitionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {

    private final FeatureDefinitionService service;

    public FeatureController(FeatureDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeatureResponse> create(@Valid @RequestBody CreateFeatureRequest request) {
        return ApiResponse.of(service.createFeature(request));
    }

    @GetMapping("/{featureKey}")
    public ApiResponse<FeatureResponse> getByKey(@PathVariable String featureKey) {
        return ApiResponse.of(service.getFeature(featureKey));
    }

    @GetMapping
    public ApiResponse<PageResponse<FeatureResponse>> list(
            @RequestParam(required = false) FeatureStatus status,
            @PageableDefault(size = 50, sort = "featureKey") Pageable pageable) {

        Page<FeatureResponse> page = service.listFeatures(status, pageable);
        return ApiResponse.of(PageResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements()));
    }

    @PatchMapping("/{featureKey}")
    public ApiResponse<FeatureResponse> update(@PathVariable String featureKey,
                                               @Valid @RequestBody UpdateFeatureRequest request) {
        return ApiResponse.of(service.updateFeature(featureKey, request));
    }

    @PostMapping("/{featureKey}/enable")
    public ApiResponse<FeatureResponse> enable(@PathVariable String featureKey) {
        return ApiResponse.of(service.enableFeature(featureKey));
    }

    @PostMapping("/{featureKey}/disable")
    public ApiResponse<FeatureResponse> disable(@PathVariable String featureKey) {
        return ApiResponse.of(service.disableFeature(featureKey));
    }

    @PostMapping("/{featureKey}/deprecate")
    public ApiResponse<FeatureResponse> deprecate(@PathVariable String featureKey) {
        return ApiResponse.of(service.deprecateFeature(featureKey));
    }
}
