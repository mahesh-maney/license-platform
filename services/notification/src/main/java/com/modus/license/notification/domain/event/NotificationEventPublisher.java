package com.modus.license.notification.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.notification.NotificationEvent;
import com.modus.license.notification.domain.entity.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link NotificationEvent} Avro records to {@code modus.notification.events}.
 * Partition key: tenantId — all notification events for a tenant are co-partitioned.
 */
@Component
public class NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(NotificationEntity entity) {
        String eventType = "SENT".equals(entity.getStatus())
                ? EventTypes.Notification.SENT
                : EventTypes.Notification.FAILED;
        try {
            NotificationEvent event = buildEvent(entity, eventType);
            kafkaTemplate.send(TopicConstants.NOTIFICATION_EVENTS, entity.getTenantId().toString(), event);
            log.debug("Published notification event: type={} notificationId={} tenantId={}",
                    eventType, entity.getId(), entity.getTenantId());
        } catch (Exception e) {
            log.error("Failed to publish notification event for notificationId={}: {}",
                    entity.getId(), e.getMessage());
        }
    }

    private NotificationEvent buildEvent(NotificationEntity entity, String eventType) {
        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(entity.getTenantId().toString())
                .setActorId(null)
                .setTimestamp(Instant.now())
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return NotificationEvent.newBuilder()
                .setMetadata(metadata)
                .setNotificationId(entity.getId().toString())
                .setTenantId(entity.getTenantId().toString())
                .setRecipientId(null)
                .setRecipientEmail(entity.getRecipientEmail())
                .setNotificationType(entity.getNotificationType())
                .setChannel(entity.getChannel())
                .setTemplateId(null)
                .setSubject(entity.getSubject())
                .setPayloadJson(entity.getPayloadJson())
                .setWebhookUrl(entity.getWebhookUrl())
                .setStatus(entity.getStatus())
                .setRetryCount(entity.getRetryCount())
                .setFailureReason(entity.getFailureReason())
                .build();
    }
}
