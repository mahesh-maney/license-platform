package com.modus.license.feature.domain.entity;

import com.modus.license.core.domain.enums.FeatureStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-tenant override of a feature's global status.
 *
 * When this record exists for (tenantId, featureKey), the override status
 * is used instead of the global {@link FeatureDefinitionEntity#getStatus()}.
 *
 * {@code featureKey} is denormalised to avoid a join in event publishing.
 */
@Entity
@Table(
    name = "tenant_feature_overrides",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tenant_feature", columnNames = {"tenant_id", "feature_key"})
)
@Getter
@Setter
@NoArgsConstructor
public class TenantFeatureOverrideEntity extends JpaBaseEntity {

    /** Owning tenant — plain UUID, no cross-DB FK. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_id", nullable = false, updatable = false)
    private FeatureDefinitionEntity feature;

    /** Denormalised for event publishing without an extra join. */
    @Column(name = "feature_key", nullable = false, updatable = false, length = 100)
    private String featureKey;

    /** The overridden status for this tenant. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeatureStatus status;

    /** Optional JSON configuration blob for this tenant's override. */
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;
}
