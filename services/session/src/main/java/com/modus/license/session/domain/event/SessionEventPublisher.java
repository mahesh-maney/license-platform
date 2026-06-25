package com.modus.license.session.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.session.SessionEvent;
import com.modus.license.session.domain.model.SessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link SessionEvent} Avro records to {@code modus.session.events}.
 * Partition key: sessionId — all events for a session are ordered.
 */
@Component
public class SessionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SessionEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SessionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishStarted(SessionRecord session) {
        publish(build(session, EventTypes.Session.STARTED, null, null));
    }

    public void publishHeartbeat(SessionRecord session) {
        publish(build(session, EventTypes.Session.HEARTBEAT, null, null));
    }

    public void publishEnded(SessionRecord session, String endReason) {
        publish(build(session, EventTypes.Session.ENDED, Instant.now(), endReason));
    }

    public void publishKilled(SessionRecord session, String endReason) {
        publish(build(session, EventTypes.Session.KILLED, Instant.now(), endReason));
    }

    public void publishExpired(SessionRecord session) {
        publish(build(session, EventTypes.Session.EXPIRED, Instant.now(), "TIMEOUT"));
    }

    private SessionEvent build(SessionRecord session, String eventType,
                                Instant endedAt, String endReason) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(session.tenantId())
                .setActorId(session.userId())
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return SessionEvent.newBuilder()
                .setMetadata(metadata)
                .setSessionId(session.sessionId())
                .setTenantId(session.tenantId())
                .setUserId(session.userId())
                .setLicenseId(session.licenseId())
                .setClientIp(session.clientIp())
                .setUserAgent(session.userAgent())
                .setStartedAt(session.startedAt())
                .setLastSeenAt(session.lastSeenAt())
                .setEndedAt(endedAt)
                .setEndReason(endReason)
                .build();
    }

    private void publish(SessionEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.SESSION_EVENTS, event.getSessionId(), event);
            log.debug("Published session event: type={} sessionId={}",
                    event.getMetadata().getEventType(), event.getSessionId());
        } catch (Exception e) {
            log.error("Failed to publish session event: type={} sessionId={} error={}",
                    event.getMetadata().getEventType(), event.getSessionId(), e.getMessage());
        }
    }
}
