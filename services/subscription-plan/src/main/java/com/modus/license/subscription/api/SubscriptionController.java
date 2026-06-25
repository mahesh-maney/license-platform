package com.modus.license.subscription.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.subscription.api.dto.CreateSubscriptionRequest;
import com.modus.license.subscription.api.dto.SubscriptionResponse;
import com.modus.license.subscription.domain.enums.SubscriptionStatus;
import com.modus.license.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriptionResponse> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        return ApiResponse.of(service.createSubscription(request));
    }

    @GetMapping("/active")
    public ApiResponse<SubscriptionResponse> getActive() {
        return ApiResponse.of(service.getActiveSubscription());
    }

    @GetMapping("/{id}")
    public ApiResponse<SubscriptionResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(service.getSubscription(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<SubscriptionResponse>> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @PageableDefault(size = 20, sort = "startDate") Pageable pageable) {

        Page<SubscriptionResponse> page = (status != null)
                ? service.listByStatus(status, pageable)
                : service.listSubscriptions(pageable);

        return ApiResponse.of(PageResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements()));
    }

    @PostMapping("/{id}/change-plan")
    public ApiResponse<SubscriptionResponse> changePlan(@PathVariable UUID id,
                                                        @RequestParam UUID newPlanId) {
        return ApiResponse.of(service.changePlan(id, newPlanId));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<SubscriptionResponse> cancel(@PathVariable UUID id,
                                                    @RequestParam(required = false) String reason) {
        return ApiResponse.of(service.cancelSubscription(id, reason));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<SubscriptionResponse> suspend(@PathVariable UUID id) {
        return ApiResponse.of(service.suspendSubscription(id));
    }

    @PostMapping("/{id}/renew")
    public ApiResponse<SubscriptionResponse> renew(@PathVariable UUID id,
                                                   @RequestParam(required = false) Instant newEndDate) {
        return ApiResponse.of(service.renewSubscription(id, newEndDate));
    }
}
