package com.modus.license.reporting.domain.repository;

import com.modus.license.reporting.domain.entity.EntitlementSnapshotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EntitlementSnapshotRepository extends JpaRepository<EntitlementSnapshotEntity, UUID> {

    Page<EntitlementSnapshotEntity> findByTenantIdOrderByRecordedAtDesc(UUID tenantId, Pageable pageable);

    List<EntitlementSnapshotEntity> findByTenantId(UUID tenantId);
}
