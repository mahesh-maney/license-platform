package com.modus.license.usage.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.usage.UsageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link UsageEvent} Avro records to {@code modus.usage.events}.
 * Partition key: tenantId — all usage events for a tenant are co-partitioned.
 */
@Component
public class UsageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UsageEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UsageEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishRecorded(String tenantId, String userId, String featureKey,
                                 String metricName, double quantity, String unit) {
        Instant now = Instant.now();
        publish(build(EventTypes.Usage.RECORDED, tenantId, userId, featureKey,
                metricName, quantity, unit, null, null, now, now));
    }

    public void publishAggregated(String tenantId, String featureKey, String metricName,
                                   double quantity, String unit, double cumulativeTotal,
                                   Instant windowStart, Instant windowEnd) {
        publish(build(EventTypes.Usage.AGGREGATED, tenantId, null, featureKey,
                metricName, quantity, unit, cumulativeTotal, null, windowStart, windowEnd));
    }

    public void publishThresholdReached(String tenantId, String featureKey, String metricName,
                                         double cumulativeTotal, double threshold,
                                         Instant windowStart, Instant windowEnd) {
        publish(build(EventTypes.Usage.THRESHOLD_REACHED, tenantId, null, featureKey,
                metricName, 0.0, "NONE", cumulativeTotal, threshold, windowStart, windowEnd));
    }

    public void publishLimitExceeded(String tenantId, String featureKey, String metricName,
                                      double cumulativeTotal, double limit,
                                      Instant windowStart, Instant windowEnd) {
        publish(build(EventTypes.Usage.LIMIT_EXCEEDED, tenantId, null, featureKey,
                metricName, 0.0, "NONE", cumulativeTotal, limit, windowStart, windowEnd));
    }

    private UsageEvent build(String eventType, String tenantId, String userId,
                              String featureKey, String metricName, double quantity, String unit,
                              Double cumulativeTotal, Double threshold,
                              Instant windowStart, Instant windowEnd) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(tenantId)
                .setActorId(userId)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return UsageEvent.newBuilder()
                .setMetadata(metadata)
                .setUsageId(UUID.randomUUID().toString())
                .setTenantId(tenantId)
                .setUserId(userId)
                .setFeatureKey(featureKey)
                .setMetricName(metricName)
                .setQuantity(quantity)
                .setUnit(unit)
                .setCumulativeTotal(cumulativeTotal)
                .setThreshold(threshold)
                .setWindowStart(windowStart)
                .setWindowEnd(windowEnd)
                .build();
    }

    private void publish(UsageEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.USAGE_EVENTS, event.getTenantId(), event);
            log.debug("Published usage event: type={} tenantId={} featureKey={} metric={}",
                    event.getMetadata().getEventType(), event.getTenantId(),
                    event.getFeatureKey(), event.getMetricName());
        } catch (Exception e) {
            log.error("Failed to publish usage event for tenant={}: {}",
                    event.getTenantId(), e.getMessage());
        }
    }
}
