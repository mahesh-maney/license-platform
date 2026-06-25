package com.modus.license.notification.domain.repository;

import com.modus.license.notification.domain.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
