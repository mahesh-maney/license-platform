package com.modus.license.enforcement.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.enforcement.api.dto.CheckAccessRequest;
import com.modus.license.enforcement.api.dto.CheckAccessResponse;
import com.modus.license.enforcement.service.EnforcementService;
import com.modus.license.security.filter.ReactorTenantContextUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * HTTP policy enforcement endpoint.
 *
 * Callers send the feature they want to access; the engine returns
 * an ALLOWED or DENIED decision with optional denial reason.
 */
@RestController
@RequestMapping("/api/v1/enforcement")
public class EnforcementController {

    private final EnforcementService enforcementService;

    public EnforcementController(EnforcementService enforcementService) {
        this.enforcementService = enforcementService;
    }

    /**
     * POST /api/v1/enforcement/check
     *
     * Evaluate whether the authenticated user may access the requested feature.
     * tenantId is resolved from the JWT.
     */
    @PostMapping("/check")
    public Mono<ResponseEntity<ApiResponse<CheckAccessResponse>>> checkAccess(
            @Valid @RequestBody CheckAccessRequest request) {
        return ReactorTenantContextUtil.getTenantContext()
                .flatMap(ctx -> enforcementService.checkAccess(
                        ctx.tenantId().value().toString(), request))
                .map(ApiResponse::of)
                .map(ResponseEntity::ok);
    }
}
