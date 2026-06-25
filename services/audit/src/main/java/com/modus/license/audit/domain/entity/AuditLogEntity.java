package com.modus.license.audit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log record, persisted from a consumed {@code AuditEvent}.
 *
 * Records are written once and never updated.
 * {@code contentHash} (SHA-256) provides tamper-evidence.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntity {

    /** UUID from the AuditEvent — stable across replay. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 255)
    private String actorId;

    /** USER | SERVICE_ACCOUNT | SYSTEM */
    @Column(name = "actor_type", nullable = false, updatable = false, length = 50)
    private String actorType;

    /** CREATE | UPDATE | DELETE | SUSPEND | ACTIVATE | ASSIGN | REVOKE | LOGIN | LOGOUT … */
    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 255)
    private String resourceId;

    /** SUCCESS | FAILURE | DENIED */
    @Column(name = "outcome", nullable = false, updatable = false, length = 50)
    private String outcome;

    @Column(name = "service_name", nullable = false, updatable = false, length = 100)
    private String serviceName;

    @Column(name = "ip_address", updatable = false, length = 50)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @Column(name = "request_id", updatable = false, length = 255)
    private String requestId;

    /** JSON snapshot of the resource before the change. */
    @Column(name = "before_state", updatable = false, columnDefinition = "TEXT")
    private String beforeState;

    /** JSON snapshot of the resource after the change. */
    @Column(name = "after_state", updatable = false, columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "failure_reason", updatable = false, columnDefinition = "TEXT")
    private String failureReason;

    /** Event timestamp from metadata — the moment the action occurred. */
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    /** SHA-256 of key fields for tamper-evidence. */
    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;
}
