package com.modus.license.entitlement.domain.entity;

import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "entitlements")
@Getter
@Setter
@NoArgsConstructor
public class EntitlementEntity extends JpaBaseEntity {

    /** Owning tenant — plain UUID, no cross-service FK. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * The subscription that originated this entitlement.
     * Plain UUID — subscription lives in subscription-plan service DB.
     */
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    /** The plan this entitlement is derived from. */
    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 20)
    private PlanTier planTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false, length = 30)
    private LicenseType licenseType;

    /** Effective seat limit. Null = unlimited. */
    @Column(name = "seat_limit")
    private Integer seatLimit;

    /**
     * Feature keys this entitlement grants.
     * Initially empty — populated by FeatureEvent consumer as features
     * are enabled per plan tier, and by manual updates.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "entitlement_features", joinColumns = @JoinColumn(name = "entitlement_id"))
    @Column(name = "feature_key", length = 150)
    private Set<String> featureKeys = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntitlementStatus status;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    /** Null = evergreen. */
    @Column(name = "end_date")
    private Instant endDate;
}
