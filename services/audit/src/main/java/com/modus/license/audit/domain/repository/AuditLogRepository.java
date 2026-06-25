package com.modus.license.audit.domain.repository;

import com.modus.license.audit.domain.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    /**
     * Flexible search with all optional filters.
     * NULL parameters are treated as "no filter" (match all values).
     */
    @Query("""
            SELECT a FROM AuditLogEntity a
            WHERE a.tenantId = :tenantId
              AND (:actorId       IS NULL OR a.actorId       = :actorId)
              AND (:resourceType  IS NULL OR a.resourceType  = :resourceType)
              AND (:resourceId    IS NULL OR a.resourceId    = :resourceId)
              AND (:outcome       IS NULL OR a.outcome       = :outcome)
              AND (:from          IS NULL OR a.recordedAt   >= :from)
              AND (:to            IS NULL OR a.recordedAt   <= :to)
            ORDER BY a.recordedAt DESC
            """)
    Page<AuditLogEntity> search(
            @Param("tenantId")      UUID tenantId,
            @Param("actorId")       String actorId,
            @Param("resourceType")  String resourceType,
            @Param("resourceId")    String resourceId,
            @Param("outcome")       String outcome,
            @Param("from")          Instant from,
            @Param("to")            Instant to,
            Pageable pageable
    );
}
