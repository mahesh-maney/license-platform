package com.modus.license.namedlicense.domain.repository;

import com.modus.license.namedlicense.domain.entity.SeatAssignmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignmentEntity, UUID> {

    Page<SeatAssignmentEntity> findByLicenseId(UUID licenseId, Pageable pageable);

    Optional<SeatAssignmentEntity> findByLicenseIdAndUserId(UUID licenseId, UUID userId);

    boolean existsByLicenseIdAndUserId(UUID licenseId, UUID userId);
}
