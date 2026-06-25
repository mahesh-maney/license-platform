package com.modus.license.feature.domain.entity;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Global feature definition — the platform's feature catalog.
 *
 * {@code featureKey} is the stable string identifier used in code and events
 * (e.g. "ADVANCED_REPORTING"). Once set it must never change.
 *
 * {@code minimumPlanTier} gates the feature at the plan level.
 * Tenant overrides (ENABLED/DISABLED/BETA per tenant) live in
 * {@link TenantFeatureOverrideEntity}.
 */
@Entity
@Table(name = "feature_definitions")
@Getter
@Setter
@NoArgsConstructor
public class FeatureDefinitionEntity extends JpaBaseEntity {

    @Column(name = "feature_key", nullable = false, unique = true, updatable = false, length = 100)
    private String featureKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Minimum plan tier required to access this feature.
     * Null = available to all plan tiers.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_plan_tier", length = 20)
    private PlanTier minimumPlanTier;

    /** Default status when no tenant override exists. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeatureStatus status;

    /**
     * JSON Schema (as a string) describing allowed values for per-tenant
     * {@code configJson}. Used for validation at the API layer.
     */
    @Column(name = "config_schema_json", columnDefinition = "TEXT")
    private String configSchemaJson;
}
