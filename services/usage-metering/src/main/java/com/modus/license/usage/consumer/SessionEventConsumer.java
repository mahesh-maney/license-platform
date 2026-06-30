package com.modus.license.usage.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.session.SessionEvent;
import com.modus.license.usage.domain.event.UsageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Converts session lifecycle events into usage records.
 *
 * SESSION_STARTED → 1 active session (concurrency slot consumed).
 * SESSION_ENDED / SESSION_KILLED / SESSION_EXPIRED → session duration in seconds.
 */
@Component
public class SessionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SessionEventConsumer.class);

    private static final String FEATURE_SESSION         = "SESSION";
    private static final String METRIC_ACTIVE_SESSIONS  = "ACTIVE_SESSIONS";
    private static final String METRIC_SESSION_DURATION = "SESSION_DURATION_SECONDS";
    private static final String UNIT_SESSIONS           = "SESSIONS";
    private static final String UNIT_SECONDS            = "SECONDS";

    private final UsageEventPublisher publisher;

    public SessionEventConsumer(UsageEventPublisher publisher) {
        this.publisher = publisher;
    }

    @KafkaListener(
            topics = TopicConstants.SESSION_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSessionEvent(SessionEvent event) {
        String eventType = event.getMetadata().getEventType();

        try {
            switch (eventType) {
                case EventTypes.Session.STARTED  -> recordSessionStart(event);
                case EventTypes.Session.ENDED,
                     EventTypes.Session.KILLED,
                     EventTypes.Session.EXPIRED  -> recordSessionEnd(event);
                default -> { /* HEARTBEAT — no usage recording */ }
            }
        } catch (Exception e) {
            log.error("Failed to record usage for session event: type={} sessionId={} error={}",
                    eventType, event.getSessionId(), e.getMessage());
        }
    }

    private void recordSessionStart(SessionEvent event) {
        publisher.publishRecorded(
                event.getTenantId(),
                event.getUserId(),
                FEATURE_SESSION,
                METRIC_ACTIVE_SESSIONS,
                1.0,
                UNIT_SESSIONS
        );
        log.debug("Recorded session start usage: tenantId={} sessionId={} userId={}",
                event.getTenantId(), event.getSessionId(), event.getUserId());
    }

    private void recordSessionEnd(SessionEvent event) {
        if (event.getEndedAt() == null) {
            log.debug("Session end event has no endedAt, skipping duration recording: sessionId={}", event.getSessionId());
            return;
        }
        long durationSeconds = Duration.between(
                event.getStartedAt(), event.getEndedAt()).getSeconds();
        if (durationSeconds < 0) durationSeconds = 0;

        publisher.publishRecorded(
                event.getTenantId(),
                event.getUserId(),
                FEATURE_SESSION,
                METRIC_SESSION_DURATION,
                durationSeconds,
                UNIT_SECONDS
        );
        log.debug("Recorded session end usage: tenantId={} sessionId={} durationSeconds={}",
                event.getTenantId(), event.getSessionId(), durationSeconds);
    }
}
