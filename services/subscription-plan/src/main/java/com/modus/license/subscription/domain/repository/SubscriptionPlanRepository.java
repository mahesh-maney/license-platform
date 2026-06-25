package com.modus.license.subscription.domain.repository;

import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.subscription.domain.entity.SubscriptionPlanEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, UUID> {

    Optional<SubscriptionPlanEntity> findByName(String name);

    boolean existsByName(String name);

    Page<SubscriptionPlanEntity> findByActive(boolean active, Pageable pageable);

    List<SubscriptionPlanEntity> findByTierAndActive(PlanTier tier, boolean active);
}
