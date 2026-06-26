package com.modus.license.reporting.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.usage.UsageEvent;
import com.modus.license.reporting.domain.entity.UsageMetricEntity;
import com.modus.license.reporting.domain.repository.UsageMetricRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsageEventConsumer")
class UsageEventConsumerTest {

    @Mock  UsageMetricRepository repository;
    @InjectMocks UsageEventConsumer consumer;

    static final String TENANT_ID   = UUID.randomUUID().toString();
    static final String FEATURE_KEY = "EXPORT_PDF";
    static final String METRIC_NAME = "API_CALLS";
    static final Instant WIN_START  = Instant.now().minusSeconds(3600);
    static final Instant WIN_END    = Instant.now();

    private UsageEvent event(String eventType, double quantity, Double cumulativeTotal) {
        EventMetadata meta = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID)
                .setTimestamp(Instant.now())
                .build();

        return UsageEvent.newBuilder()
                .setMetadata(meta)
                .setUsageId(UUID.randomUUID().toString())
                .setTenantId(TENANT_ID)
                .setUserId(UUID.randomUUID().toString())
                .setFeatureKey(FEATURE_KEY)
                .setMetricName(METRIC_NAME)
                .setQuantity(quantity)
                .setUnit("CALLS")
                .setCumulativeTotal(cumulativeTotal)
                .setThreshold(null)
                .setWindowStart(WIN_START)
                .setWindowEnd(WIN_END)
                .build();
    }

    // ── USAGE_AGGREGATED ──────────────────────────────────────────────────────

    @Test
    @DisplayName("AGGREGATED (new window) → creates metric with cumulativeTotal when present")
    void aggregated_newMetric_withCumulativeTotal() {
        when(repository.findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onUsageEvent(event(EventTypes.Usage.AGGREGATED, 5.0, 100.0));

        ArgumentCaptor<UsageMetricEntity> cap = ArgumentCaptor.forClass(UsageMetricEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getTotalQuantity()).isEqualTo(100.0);
        assertThat(cap.getValue().getEventCount()).isEqualTo(1);
        assertThat(cap.getValue().getFeatureKey()).isEqualTo(FEATURE_KEY);
    }

    @Test
    @DisplayName("AGGREGATED (new window) → adds quantity when cumulativeTotal is null")
    void aggregated_newMetric_noCumulativeTotal() {
        when(repository.findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onUsageEvent(event(EventTypes.Usage.AGGREGATED, 7.0, null));

        ArgumentCaptor<UsageMetricEntity> cap = ArgumentCaptor.forClass(UsageMetricEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getTotalQuantity()).isEqualTo(7.0); // 0 + 7
        assertThat(cap.getValue().getEventCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AGGREGATED (existing window) → accumulates eventCount and updates total")
    void aggregated_existingMetric() {
        UsageMetricEntity existing = new UsageMetricEntity();
        existing.setTotalQuantity(50.0);
        existing.setEventCount(3);
        existing.setThresholdBreaches(0);

        when(repository.findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onUsageEvent(event(EventTypes.Usage.AGGREGATED, 10.0, 60.0));

        assertThat(existing.getTotalQuantity()).isEqualTo(60.0);
        assertThat(existing.getEventCount()).isEqualTo(4);
    }

    // ── USAGE_THRESHOLD_REACHED / USAGE_LIMIT_EXCEEDED ───────────────────────

    @Test
    @DisplayName("THRESHOLD_REACHED → increments thresholdBreaches on existing metric")
    void thresholdReached_incrementsBreaches() {
        UsageMetricEntity existing = new UsageMetricEntity();
        existing.setThresholdBreaches(2);

        when(repository.findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onUsageEvent(event(EventTypes.Usage.THRESHOLD_REACHED, 0, null));

        assertThat(existing.getThresholdBreaches()).isEqualTo(3);
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("LIMIT_EXCEEDED → increments thresholdBreaches on existing metric")
    void limitExceeded_incrementsBreaches() {
        UsageMetricEntity existing = new UsageMetricEntity();
        existing.setThresholdBreaches(0);

        when(repository.findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onUsageEvent(event(EventTypes.Usage.LIMIT_EXCEEDED, 0, null));

        assertThat(existing.getThresholdBreaches()).isEqualTo(1);
    }

    @Test
    @DisplayName("USAGE_RECORDED → skipped (no repo call)")
    void recorded_isSkipped() {
        consumer.onUsageEvent(event(EventTypes.Usage.RECORDED, 1.0, null));

        verify(repository, never()).save(any());
        verify(repository, never()).findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                any(), any(), any(), any(), any());
    }
}
