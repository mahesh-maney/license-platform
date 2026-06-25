package com.modus.license.subscription.api;

import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.subscription.api.dto.CreatePlanRequest;
import com.modus.license.subscription.api.dto.PlanResponse;
import com.modus.license.subscription.api.dto.UpdatePlanRequest;
import com.modus.license.subscription.service.SubscriptionPlanService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService service;

    public SubscriptionPlanController(SubscriptionPlanService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        return ApiResponse.of(service.createPlan(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(service.getPlan(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<PlanResponse>> list(
            @RequestParam(required = false) Boolean activeOnly,
            @PageableDefault(size = 20, sort = "tier") Pageable pageable) {

        Page<PlanResponse> page = service.listPlans(activeOnly, pageable);
        return ApiResponse.of(PageResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements()));
    }

    @GetMapping("/by-tier/{tier}")
    public ApiResponse<List<PlanResponse>> listByTier(@PathVariable PlanTier tier) {
        return ApiResponse.of(service.listByTier(tier));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PlanResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdatePlanRequest request) {
        return ApiResponse.of(service.updatePlan(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<PlanResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.of(service.deactivatePlan(id));
    }
}
