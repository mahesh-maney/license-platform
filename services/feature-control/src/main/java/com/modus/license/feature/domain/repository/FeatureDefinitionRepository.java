package com.modus.license.feature.domain.repository;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.feature.domain.entity.FeatureDefinitionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureDefinitionRepository extends JpaRepository<FeatureDefinitionEntity, UUID> {

    Optional<FeatureDefinitionEntity> findByFeatureKey(String featureKey);

    boolean existsByFeatureKey(String featureKey);

    Page<FeatureDefinitionEntity> findByStatus(FeatureStatus status, Pageable pageable);

    List<FeatureDefinitionEntity> findByMinimumPlanTierIsNullOrMinimumPlanTierLessThanEqual(PlanTier tier);
}
