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
 * Latest-state snapshot of a tenant entitlement.
 *
 * Primary key is the {@code entitlementId} from the originating event.
 * Updated in-place on every EntitlementEvent for that entitlement.
 */
@Entity
@Table(name = "entitlement_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class EntitlementSnapshotEntity {

    @Id
    @Column(name = "entitlement_id", nullable = false, updatable = false)
    private UUID entitlementId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "plan_tier", nullable = false, length = 50)
    private String planTier;

    @Column(name = "license_type", nullable = false, length = 50)
    private String licenseType;

    @Column(name = "seat_limit")
    private Integer seatLimit;

    /** Comma-separated list of feature keys granted by this entitlement. */
    @Column(name = "feature_keys", columnDefinition = "TEXT")
    private String featureKeys;

    /** ACTIVE | EXPIRED | SUSPENDED | REVOKED | PENDING */
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
