package com.modus.license.subscription.domain.repository;

import com.modus.license.subscription.domain.entity.SubscriptionEntity;
import com.modus.license.subscription.domain.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByTenantIdAndStatusIn(UUID tenantId, List<SubscriptionStatus> statuses);

    Page<SubscriptionEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<SubscriptionEntity> findByStatus(SubscriptionStatus status, Pageable pageable);

    boolean existsByTenantIdAndStatusIn(UUID tenantId, List<SubscriptionStatus> statuses);
}
