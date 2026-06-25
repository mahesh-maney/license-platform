package com.modus.license.subscription.domain.entity;

import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.subscription.domain.enums.BillingCycle;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Catalog entry for a subscription plan (e.g. "Starter Monthly").
 * Plans are global (not tenant-scoped); only PLATFORM_ADMINs manage them.
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlanEntity extends JpaBaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "plan_tier", length = 20)
    private PlanTier tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "license_type", length = 30)
    private LicenseType licenseType;

    /**
     * Default maximum seats / concurrent users. Null = unlimited (e.g. SITE licence).
     * Individual subscriptions may override this via {@code seatLimit}.
     */
    @Column(name = "max_seats")
    private Integer maxSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "billing_cycle", length = 10)
    private BillingCycle billingCycle;

    /** Price in minor currency units (e.g. cents for USD). */
    @Column(nullable = false, name = "price_in_cents")
    private Long priceInCents;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Feature keys bundled with this plan (e.g. "feature.reporting.advanced").
     * Stored in {@code plan_features} join table.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "feature_key", length = 150)
    private Set<String> features = new HashSet<>();
}
