package com.modus.license.subscription.domain.entity;

import com.modus.license.subscription.domain.enums.BillingCycle;
import com.modus.license.subscription.domain.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's active subscription to a plan.
 * A tenant should have at most one {@code ACTIVE} or {@code TRIAL} subscription at a time;
 * this is enforced by the service layer, not a DB constraint, to allow grace-period overlaps.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionEntity extends JpaBaseEntity {

    /**
     * Owning tenant. Plain UUID — no FK because tenants live in a different database.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlanEntity plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    /**
     * Per-subscription seat override. When null, inherits {@code plan.maxSeats}.
     */
    @Column(name = "seat_limit")
    private Integer seatLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "billing_cycle", length = 10)
    private BillingCycle billingCycle;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    /** Null for evergreen (auto-renewing) subscriptions. */
    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Effective seat limit: per-subscription override → plan default → unlimited. */
    public Integer effectiveSeatLimit() {
        if (seatLimit != null) return seatLimit;
        return plan.getMaxSeats();
    }
}
