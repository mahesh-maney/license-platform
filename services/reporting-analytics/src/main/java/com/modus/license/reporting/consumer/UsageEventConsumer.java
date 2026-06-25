package com.modus.license.reporting.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.usage.UsageEvent;
import com.modus.license.reporting.domain.entity.UsageMetricEntity;
import com.modus.license.reporting.domain.repository.UsageMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes {@link UsageEvent} records from {@code modus.usage.events} and
 * materialises aggregated usage metrics into the reporting database.
 *
 * <ul>
 *   <li>{@code USAGE_AGGREGATED} — upserts a {@link UsageMetricEntity} row for
 *       the (tenant, feature, metric, window) key.</li>
 *   <li>{@code USAGE_THRESHOLD_REACHED} / {@code USAGE_LIMIT_EXCEEDED} — increments
 *       the threshold-breach counter on the matching row.</li>
 *   <li>{@code USAGE_RECORDED} — skipped; raw events are not stored here.</li>
 * </ul>
 */
@Component
public class UsageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageEventConsumer.class);

    private final UsageMetricRepository repository;

    public UsageEventConsumer(UsageMetricRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @KafkaListener(
            topics           = TopicConstants.USAGE_EVENTS,
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUsageEvent(UsageEvent event) {
        String eventType = event.getMetadata().getEventType();
        try {
            switch (eventType) {
                case EventTypes.Usage.AGGREGATED          -> handleAggregated(event);
                case EventTypes.Usage.THRESHOLD_REACHED,
                     EventTypes.Usage.LIMIT_EXCEEDED      -> handleBreach(event);
                default -> log.debug("Skipping usage event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process UsageEvent type={} tenantId={}: {}",
                    eventType, event.getTenantId(), e.getMessage(), e);
            throw new RuntimeException("UsageEvent processing failed", e);
        }
    }

    private void handleAggregated(UsageEvent event) {
        UUID tenantId = UUID.fromString(event.getTenantId());

        UsageMetricEntity metric = repository
                .findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                        tenantId,
                        event.getFeatureKey(),
                        event.getMetricName(),
                        event.getWindowStart(),
                        event.getWindowEnd())
                .orElse(new UsageMetricEntity());

        metric.setTenantId(tenantId);
        metric.setFeatureKey(event.getFeatureKey());
        metric.setMetricName(event.getMetricName());
        metric.setUnit(event.getUnit());
        metric.setWindowStart(event.getWindowStart());
        metric.setWindowEnd(event.getWindowEnd());

        double newTotal = event.getCumulativeTotal() != null
                ? event.getCumulativeTotal()
                : metric.getTotalQuantity() + event.getQuantity();
        metric.setTotalQuantity(newTotal);
        metric.setEventCount(metric.getEventCount() + 1);

        repository.save(metric);
        log.debug("Upserted usage metric: tenantId={} feature={} metric={} window=[{},{}]",
                tenantId, event.getFeatureKey(), event.getMetricName(),
                event.getWindowStart(), event.getWindowEnd());
    }

    private void handleBreach(UsageEvent event) {
        UUID tenantId = UUID.fromString(event.getTenantId());

        repository.findByTenantIdAndFeatureKeyAndMetricNameAndWindowStartAndWindowEnd(
                        tenantId,
                        event.getFeatureKey(),
                        event.getMetricName(),
                        event.getWindowStart(),
                        event.getWindowEnd())
                .ifPresent(metric -> {
                    metric.setThresholdBreaches(metric.getThresholdBreaches() + 1);
                    repository.save(metric);
                    log.debug("Incremented threshold breach: tenantId={} feature={} total={}",
                            tenantId, event.getFeatureKey(), metric.getThresholdBreaches());
                });
    }
}
