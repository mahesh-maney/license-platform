package com.modus.license.namedlicense.domain.repository;

import com.modus.license.namedlicense.domain.entity.NamedLicenseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NamedLicenseRepository extends JpaRepository<NamedLicenseEntity, UUID> {

    Page<NamedLicenseEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<NamedLicenseEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<NamedLicenseEntity> findByTenantIdAndEntitlementId(UUID tenantId, UUID entitlementId);

    boolean existsByTenantIdAndEntitlementId(UUID tenantId, UUID entitlementId);
}
