package com.modus.license.session.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.session.api.dto.SessionResponse;
import com.modus.license.session.api.dto.StartSessionRequest;
import com.modus.license.session.service.SessionService;
import com.modus.license.security.filter.ReactorTenantContextUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Session lifecycle endpoints (HTTP/WebFlux).
 *
 * All routes are tenant-scoped — tenantId comes from the JWT via TenantContextWebFilter.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<SessionResponse>>> startSession(
            @Valid @RequestBody StartSessionRequest request) {
        return ReactorTenantContextUtil.getTenantContext()
                .flatMap(ctx -> sessionService.startSession(request, ctx))
                .map(ApiResponse::of)
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body));
    }

    @GetMapping("/{sessionId}")
    public Mono<ResponseEntity<ApiResponse<SessionResponse>>> getSession(
            @PathVariable String sessionId) {
        return ReactorTenantContextUtil.getTenantContext()
                .flatMap(ctx -> sessionService.getSession(sessionId, ctx))
                .map(ApiResponse::of)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{sessionId}/heartbeat")
    public Mono<ResponseEntity<ApiResponse<SessionResponse>>> heartbeat(
            @PathVariable String sessionId) {
        return ReactorTenantContextUtil.getTenantContext()
                .flatMap(ctx -> sessionService.heartbeat(sessionId, ctx))
                .map(ApiResponse::of)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{sessionId}")
    public Mono<ResponseEntity<Void>> endSession(@PathVariable String sessionId) {
        return ReactorTenantContextUtil.getTenantContext()
                .flatMap(ctx -> sessionService.endSession(sessionId, ctx))
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    @DeleteMapping("/{sessionId}/kill")
    public Mono<ResponseEntity<Void>> killSession(@PathVariable String sessionId) {
        return ReactorTenantContextUtil.getTenantContext()
                .flatMap(ctx -> sessionService.killSession(sessionId, ctx))
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<SessionResponse>>>> listActiveSessions() {
        return ReactorTenantContextUtil.getTenantContext()
                .flatMap(ctx -> sessionService.listActiveSessions(ctx).collectList())
                .map(ApiResponse::of)
                .map(ResponseEntity::ok);
    }
}
