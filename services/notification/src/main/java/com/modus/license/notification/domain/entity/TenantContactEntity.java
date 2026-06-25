package com.modus.license.notification.domain.entity;

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
 * Projection of TenantEvent: tracks the latest admin email and status
 * for each tenant so the notification service can address emails.
 *
 * Maintained by {@link com.modus.license.notification.consumer.TenantEventConsumer}.
 * Primary key is the tenantId — one record per tenant.
 */
@Entity
@Table(name = "tenant_contacts")
@Getter
@Setter
@NoArgsConstructor
public class TenantContactEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "admin_email", nullable = false, length = 255)
    private String adminEmail;

    @Column(name = "tenant_name", length = 255)
    private String tenantName;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "plan_tier", length = 50)
    private String planTier;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
