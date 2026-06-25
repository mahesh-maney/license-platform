package com.modus.license.reporting.domain.repository;

import com.modus.license.reporting.domain.entity.SubscriptionSnapshotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionSnapshotRepository extends JpaRepository<SubscriptionSnapshotEntity, UUID> {

    Page<SubscriptionSnapshotEntity> findByTenantIdOrderByRecordedAtDesc(UUID tenantId, Pageable pageable);

    List<SubscriptionSnapshotEntity> findByTenantId(UUID tenantId);
}
