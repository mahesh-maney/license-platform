package com.modus.license.namedlicense.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.namedlicense.NamedLicenseEvent;
import com.modus.license.namedlicense.domain.entity.NamedLicenseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link NamedLicenseEvent} Avro records to {@code modus.named-license.events}.
 * Partition key: licenseId — all events for a pool are ordered.
 */
@Component
public class NamedLicenseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NamedLicenseEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NamedLicenseEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishAssigned(NamedLicenseEntity license, UUID assignedUserId) {
        publish(build(license, EventTypes.NamedLicense.ASSIGNED,
                assignedUserId.toString(), null, null));
    }

    public void publishRevoked(NamedLicenseEntity license, UUID revokedUserId) {
        publish(build(license, EventTypes.NamedLicense.REVOKED,
                null, revokedUserId.toString(), null));
    }

    public void publishTransferred(NamedLicenseEntity license, UUID fromUserId, UUID toUserId) {
        publish(build(license, EventTypes.NamedLicense.TRANSFERRED,
                toUserId.toString(), fromUserId.toString(), null));
    }

    public void publishSeatLimitChanged(NamedLicenseEntity license, int previousSeatCount) {
        publish(build(license, EventTypes.NamedLicense.SEAT_LIMIT_CHANGED,
                null, null, previousSeatCount));
    }

    private NamedLicenseEvent build(NamedLicenseEntity license, String eventType,
                                     String assignedUserId, String revokedUserId,
                                     Integer previousSeatCount) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(license.getTenantId().toString())
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return NamedLicenseEvent.newBuilder()
                .setMetadata(metadata)
                .setLicenseId(license.getId().toString())
                .setTenantId(license.getTenantId().toString())
                .setPlanId(license.getPlanId().toString())
                .setTotalSeats(license.getTotalSeats())
                .setUsedSeats(license.getUsedSeats())
                .setAssignedUserId(assignedUserId)
                .setRevokedUserId(revokedUserId)
                .setPreviousSeatCount(previousSeatCount)
                .setEffectiveAt(now)
                .build();
    }

    private void publish(NamedLicenseEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.NAMED_LICENSE_EVENTS, event.getLicenseId(), event);
            log.debug("Published named-license event: type={} licenseId={}",
                    event.getMetadata().getEventType(), event.getLicenseId());
        } catch (Exception e) {
            log.error("Failed to publish named-license event: type={} licenseId={}. Error: {}",
                    event.getMetadata().getEventType(), event.getLicenseId(), e.getMessage());
        }
    }
}
