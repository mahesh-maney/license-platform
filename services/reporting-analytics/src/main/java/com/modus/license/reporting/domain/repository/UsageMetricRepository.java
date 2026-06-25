package com.modus.license.reporting.domain.repository;

import com.modus.license.reporting.domain.entity.UsageMetricEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageMetricRepository extends JpaRepository<UsageMetricEntity, UUID> {

    Optional<UsageMetricEntity> findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
            UUID tenantId, String featureKey, String metricName,
            Instant windowStart, Instant windowEnd);

    @Query("""
            SELECT m FROM UsageMetricEntity m
            WHERE m.tenantId = :tenantId
              AND (:featureKey IS NULL OR m.featureKey = :featureKey)
              AND (:metricName IS NULL OR m.metricName = :metricName)
              AND (:from      IS NULL OR m.windowStart >= :from)
              AND (:to        IS NULL OR m.windowEnd   <= :to)
            ORDER BY m.windowStart DESC
            """)
    Page<UsageMetricEntity> search(
            @Param("tenantId")   UUID tenantId,
            @Param("featureKey") String featureKey,
            @Param("metricName") String metricName,
            @Param("from")       Instant from,
            @Param("to")         Instant to,
            Pageable pageable);

    List<UsageMetricEntity> findByTenantIdAndWindowStartGreaterThanEqualAndWindowEndLessThanEqual(
            UUID tenantId, Instant from, Instant to);
}
