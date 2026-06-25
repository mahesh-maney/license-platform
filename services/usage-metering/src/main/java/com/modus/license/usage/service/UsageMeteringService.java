package com.modus.license.usage.service;

import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.usage.api.dto.RecordUsageRequest;
import com.modus.license.usage.api.dto.UsageResponse;
import com.modus.license.usage.domain.event.UsageEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class UsageMeteringService {

    private final UsageEventPublisher publisher;

    public UsageMeteringService(UsageEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Record a single usage event on behalf of the authenticated tenant.
     * The usage is published to {@code modus.usage.events} as a USAGE_RECORDED event
     * and persisted by the downstream reporting-analytics service.
     */
    @PreAuthorize("isAuthenticated()")
    public UsageResponse recordUsage(RecordUsageRequest request) {
        String tenantId = TenantContextHolder.require().tenantId().value().toString();
        String userId   = request.userId() != null ? request.userId().toString() : null;
        String usageId  = UUID.randomUUID().toString();
        Instant now     = Instant.now();

        publisher.publishRecorded(
                tenantId,
                userId,
                request.featureKey(),
                request.metricName(),
                request.quantity(),
                request.unit()
        );

        return new UsageResponse(
                usageId,
                tenantId,
                userId,
                request.featureKey(),
                request.metricName(),
                request.quantity(),
                request.unit(),
                now
        );
    }
}
