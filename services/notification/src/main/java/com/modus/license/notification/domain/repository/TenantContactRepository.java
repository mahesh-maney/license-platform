package com.modus.license.notification.domain.repository;

import com.modus.license.notification.domain.entity.TenantContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantContactRepository extends JpaRepository<TenantContactEntity, UUID> {
}
