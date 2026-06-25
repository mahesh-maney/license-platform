package com.modus.license.audit.publisher;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.core.context.TenantContext;
import com.modus.license.events.audit.AuditEvent;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.EventTypes;

import java.time.Instant;
import java.util.UUID;

// Note: Avro plugin maps timestamp-millis logical type to java.time.Instant

/**
 * Convenience factory for constructing {@link AuditEvent} Avro records.
 *
 * Services and the {@link com.modus.license.audit.aspect.AuditableAspect} use
 * this class to build well-formed audit events without repeating boilerplate.
 */
public final class AuditEventBuilder {

    private AuditEventBuilder() {}

    /**
     * Builds a SUCCESS audit event.
     *
     * @param ctx          the tenant context of the acting user
     * @param action       the action performed
     * @param resourceType domain object type (e.g. TENANT, USER)
     * @param resourceId   UUID string of the affected resource (nullable)
     * @param before       JSON snapshot before the change (nullable)
     * @param after        JSON snapshot after the change (nullable)
     * @param serviceName  name of the emitting microservice
     */
    public static AuditEvent success(
            TenantContext ctx,
            AuditAction action,
            String resourceType,
            String resourceId,
            String before,
            String after,
            String serviceName
    ) {
        return build(ctx, action, resourceType, resourceId, "SUCCESS", null, before, after, serviceName);
    }

    /**
     * Builds a SUCCESS audit event without before/after snapshots (most common case).
     */
    public static AuditEvent success(
            TenantContext ctx,
            AuditAction action,
            String resourceType,
            String resourceId,
            String serviceName
    ) {
        return build(ctx, action, resourceType, resourceId, "SUCCESS", null, null, null, serviceName);
    }

    /**
     * Builds a FAILURE audit event.
     *
     * @param failureReason human-readable reason for the failure
     */
    public static AuditEvent failure(
            TenantContext ctx,
            AuditAction action,
            String resourceType,
            String resourceId,
            String failureReason,
            String serviceName
    ) {
        return build(ctx, action, resourceType, resourceId, "FAILURE", failureReason, null, null, serviceName);
    }

    /**
     * Builds a DENIED audit event (authorization check failed).
     */
    public static AuditEvent denied(
            TenantContext ctx,
            AuditAction action,
            String resourceType,
            String resourceId,
            String reason,
            String serviceName
    ) {
        return build(ctx, action, resourceType, resourceId, "DENIED", reason, null, null, serviceName);
    }

    private static AuditEvent build(
            TenantContext ctx,
            AuditAction action,
            String resourceType,
            String resourceId,
            String outcome,
            String failureReason,
            String before,
            String after,
            String serviceName
    ) {
        String  auditId = UUID.randomUUID().toString();
        Instant now     = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(EventTypes.Audit.RECORDED)
                .setTenantId(ctx.tenantId().toString())
                .setActorId(ctx.userId().toString())
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return AuditEvent.newBuilder()
                .setMetadata(metadata)
                .setAuditId(auditId)
                .setTenantId(ctx.tenantId().toString())
                .setActorId(ctx.userId().toString())
                .setActorType("USER")
                .setAction(action.name())
                .setResourceType(resourceType)
                .setResourceId(resourceId != null ? resourceId : "")
                .setOutcome(outcome)
                .setServiceName(serviceName)
                .setIpAddress(null)
                .setUserAgent(null)
                .setRequestId(null)
                .setBefore(before)
                .setAfter(after)
                .setFailureReason(failureReason)
                .build();
    }
}
