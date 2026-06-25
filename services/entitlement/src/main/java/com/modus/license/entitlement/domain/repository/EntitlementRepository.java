package com.modus.license.entitlement.domain.repository;

import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.entitlement.domain.entity.EntitlementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntitlementRepository extends JpaRepository<EntitlementEntity, UUID> {

    Optional<EntitlementEntity> findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, List<EntitlementStatus> statuses);

    Optional<EntitlementEntity> findFirstBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);

    List<EntitlementEntity> findByTenantIdAndStatus(UUID tenantId, EntitlementStatus status);

    Page<EntitlementEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
