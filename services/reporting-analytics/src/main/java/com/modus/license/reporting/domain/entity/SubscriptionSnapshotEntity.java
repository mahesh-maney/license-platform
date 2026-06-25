package com.modus.license.reporting.domain.entity;

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
 * Latest-state snapshot of a tenant subscription.
 *
 * Primary key is the {@code subscriptionId} from the originating event.
 * Updated in-place on every SubscriptionEvent for that subscription.
 * Status is derived from the incoming event type.
 */
@Entity
@Table(name = "subscription_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionSnapshotEntity {

    @Id
    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "plan_tier", nullable = false, length = 50)
    private String planTier;

    @Column(name = "license_type", nullable = false, length = 50)
    private String licenseType;

    @Column(name = "billing_cycle", nullable = false, length = 50)
    private String billingCycle;

    @Column(name = "seat_limit")
    private Integer seatLimit;

    /** Derived from event type: PENDING | ACTIVE | SUSPENDED | CANCELLED | EXPIRED */
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
