package com.modus.license.reporting.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Pre-aggregated usage metric per (tenant, featureKey, metricName, window).
 *
 * Populated from {@code USAGE_AGGREGATED} events consumed from {@code modus.usage.events}.
 * Each row is upserted: existing rows accumulate quantity and event counts.
 */
@Entity
@Table(
    name = "usage_metrics",
    uniqueConstraints = @UniqueConstraint(
        name  = "uq_usage_metrics_key",
        columnNames = {"tenant_id", "feature_key", "metric_name", "window_start", "window_end"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class UsageMetricEntity extends JpaBaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "feature_key", nullable = false, length = 255)
    private String featureKey;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "total_quantity", nullable = false)
    private double totalQuantity;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    /** Number of USAGE_THRESHOLD_REACHED + USAGE_LIMIT_EXCEEDED events in this window. */
    @Column(name = "threshold_breaches", nullable = false)
    private int thresholdBreaches;
}
