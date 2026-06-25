package com.modus.license.user.domain.repository;

import com.modus.license.user.domain.entity.UserEntity;
import com.modus.license.user.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<UserEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    Page<UserEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<UserEntity> findByTenantIdAndStatus(UUID tenantId, UserStatus status, Pageable pageable);
}
