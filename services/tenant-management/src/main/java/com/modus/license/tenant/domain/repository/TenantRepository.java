package com.modus.license.tenant.domain.repository;

import com.modus.license.core.domain.enums.TenantStatus;
import com.modus.license.tenant.domain.entity.TenantEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByAdminEmail(String adminEmail);

    Page<TenantEntity> findByStatus(TenantStatus status, Pageable pageable);

    Page<TenantEntity> findByRegion(String region, Pageable pageable);
}
