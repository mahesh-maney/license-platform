package com.modus.license.enforcement.service;

import com.modus.license.enforcement.api.dto.CheckAccessRequest;
import com.modus.license.enforcement.api.dto.CheckAccessResponse;
import com.modus.license.enforcement.client.SessionGrpcClient;
import com.modus.license.enforcement.config.EnforcementProperties;
import com.modus.license.enforcement.domain.cache.EntitlementCacheService;
import com.modus.license.enforcement.domain.event.EnforcementDecisionPublisher;
import com.modus.license.enforcement.domain.model.CachedEntitlement;
import com.modus.license.events.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Core policy decision point (PDP).
 *
 * Decision flow:
 * 1. Load entitlement from Redis (cache-first)
 * 2. Check entitlement is ACTIVE
 * 3. Check feature key is granted
 * 4. For CONCURRENT license: check active session count via gRPC
 * 5. Publish EnforcementDecision event
 * 6. Return result
 */
@Service
public class EnforcementService {

    private static final Logger log = LoggerFactory.getLogger(EnforcementService.class);

    // Denial reason constants matching the Avro schema doc
    private static final String DENY_NO_ENTITLEMENT    = "ENTITLEMENT_NOT_FOUND";
    private static final String DENY_NOT_ACTIVE        = "ENTITLEMENT_NOT_ACTIVE";
    private static final String DENY_FEATURE_DISABLED  = "FEATURE_DISABLED";
    private static final String DENY_SESSION_EXCEEDED  = "SESSION_LIMIT_EXCEEDED";

    private final EntitlementCacheService cacheService;
    private final SessionGrpcClient sessionClient;
    private final EnforcementDecisionPublisher publisher;
    private final EnforcementProperties props;

    public EnforcementService(EntitlementCacheService cacheService,
                               SessionGrpcClient sessionClient,
                               EnforcementDecisionPublisher publisher,
                               EnforcementProperties props) {
        this.cacheService  = cacheService;
        this.sessionClient = sessionClient;
        this.publisher     = publisher;
        this.props         = props;
    }

    public Mono<CheckAccessResponse> checkAccess(String tenantId, CheckAccessRequest request) {
        long startMs = System.currentTimeMillis();

        return cacheService.get(tenantId)
                .flatMap(entitlement -> evaluate(tenantId, request, entitlement, startMs, true))
                .switchIfEmpty(deny(tenantId, request, DENY_NO_ENTITLEMENT, startMs, false))
                .timeout(Duration.ofMillis(props.decisionTimeoutMs()))
                .onErrorResume(e -> {
                    log.error("Enforcement check failed for tenant={} feature={}: {}",
                            tenantId, request.featureKey(), e.getMessage());
                    return deny(tenantId, request, DENY_NO_ENTITLEMENT, startMs, false);
                });
    }

    private Mono<CheckAccessResponse> evaluate(String tenantId, CheckAccessRequest request,
                                                CachedEntitlement entitlement,
                                                long startMs, boolean cacheHit) {
        // 1. Check entitlement is active
        if (!entitlement.isActive()) {
            return deny(tenantId, request, DENY_NOT_ACTIVE, startMs, cacheHit);
        }

        // 2. Check feature is granted
        if (!entitlement.grantsFeature(request.featureKey())) {
            return deny(tenantId, request, DENY_FEATURE_DISABLED, startMs, cacheHit);
        }

        // 3. For concurrent licenses, check session count
        if (entitlement.isConcurrent() && entitlement.seatLimit() != null) {
            return sessionClient.getActiveSessionCount(tenantId)
                    .flatMap(count -> {
                        if (count >= entitlement.seatLimit()) {
                            return deny(tenantId, request, DENY_SESSION_EXCEEDED, startMs, cacheHit);
                        }
                        return allow(tenantId, request, entitlement, startMs, cacheHit);
                    });
        }

        return allow(tenantId, request, entitlement, startMs, cacheHit);
    }

    private Mono<CheckAccessResponse> allow(String tenantId, CheckAccessRequest request,
                                             CachedEntitlement entitlement,
                                             long startMs, boolean cacheHit) {
        long ms = System.currentTimeMillis() - startMs;
        publisher.publishAllowed(tenantId, request.userId().toString(),
                request.featureKey(), entitlement.licenseType(),
                entitlement.entitlementId(), request.sessionId(), ms, cacheHit);
        return Mono.just(new CheckAccessResponse(
                true, EventTypes.Enforcement.ALLOWED, null,
                entitlement.entitlementId(), ms, cacheHit));
    }

    private Mono<CheckAccessResponse> deny(String tenantId, CheckAccessRequest request,
                                            String reason, long startMs, boolean cacheHit) {
        long ms = System.currentTimeMillis() - startMs;
        String licenseType = "UNKNOWN";
        publisher.publishDenied(tenantId, request.userId() != null ? request.userId().toString() : "unknown",
                request.featureKey(), licenseType, reason, request.sessionId(), ms, cacheHit);
        return Mono.just(new CheckAccessResponse(
                false, EventTypes.Enforcement.DENIED, reason, null, ms, cacheHit));
    }
}
