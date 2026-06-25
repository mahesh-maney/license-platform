package com.modus.license.feature.domain.repository;

import com.modus.license.feature.domain.entity.TenantFeatureOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantFeatureOverrideRepository extends JpaRepository<TenantFeatureOverrideEntity, UUID> {

    List<TenantFeatureOverrideEntity> findByTenantId(UUID tenantId);

    Optional<TenantFeatureOverrideEntity> findByTenantIdAndFeatureKey(UUID tenantId, String featureKey);

    boolean existsByTenantIdAndFeatureKey(UUID tenantId, String featureKey);

    void deleteByTenantIdAndFeatureKey(UUID tenantId, String featureKey);
}
