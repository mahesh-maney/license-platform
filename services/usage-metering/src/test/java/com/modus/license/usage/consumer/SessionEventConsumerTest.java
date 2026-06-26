package com.modus.license.usage.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.session.SessionEvent;
import com.modus.license.usage.domain.event.UsageEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionEventConsumer")
class SessionEventConsumerTest {

    @Mock  UsageEventPublisher publisher;
    @InjectMocks SessionEventConsumer consumer;

    static final String TENANT_ID = UUID.randomUUID().toString();
    static final String USER_ID   = UUID.randomUUID().toString();

    private SessionEvent event(String eventType, Instant startedAt, Instant endedAt) {
        EventMetadata meta = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID)
                .setTimestamp(Instant.now())
                .build();

        return SessionEvent.newBuilder()
                .setMetadata(meta)
                .setSessionId(UUID.randomUUID().toString())
                .setTenantId(TENANT_ID)
                .setUserId(USER_ID)
                .setStartedAt(startedAt)
                .setLastSeenAt(startedAt)
                .setEndedAt(endedAt)
                .build();
    }

    // ── SESSION_STARTED ───────────────────────────────────────────────────────

    @Test
    @DisplayName("SESSION_STARTED → publishes ACTIVE_SESSIONS = 1")
    void started_publishesActiveSession() {
        consumer.onSessionEvent(event(EventTypes.Session.STARTED, Instant.now(), null));

        verify(publisher).publishRecorded(
                TENANT_ID, USER_ID, "SESSION", "ACTIVE_SESSIONS", 1.0, "SESSIONS");
    }

    // ── SESSION_ENDED ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SESSION_ENDED with endedAt → publishes SESSION_DURATION_SECONDS")
    void ended_withEndedAt_publishesDuration() {
        Instant start = Instant.now().minusSeconds(120);
        Instant end   = Instant.now();

        consumer.onSessionEvent(event(EventTypes.Session.ENDED, start, end));

        verify(publisher).publishRecorded(
                anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.eq("SESSION_DURATION_SECONDS"),
                anyDouble(), anyString());
    }

    @Test
    @DisplayName("SESSION_ENDED with null endedAt → no usage recorded")
    void ended_nullEndedAt_noUsage() {
        consumer.onSessionEvent(event(EventTypes.Session.ENDED, Instant.now(), null));

        verify(publisher, never()).publishRecorded(
                anyString(), anyString(), anyString(), anyString(), anyDouble(), anyString());
    }

    // ── SESSION_KILLED ────────────────────────────────────────────────────────

    @Test
    @DisplayName("SESSION_KILLED with endedAt → publishes SESSION_DURATION_SECONDS")
    void killed_withEndedAt_publishesDuration() {
        Instant start = Instant.now().minusSeconds(60);
        Instant end   = Instant.now();

        consumer.onSessionEvent(event(EventTypes.Session.KILLED, start, end));

        verify(publisher).publishRecorded(
                anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.eq("SESSION_DURATION_SECONDS"),
                anyDouble(), anyString());
    }

    // ── SESSION_EXPIRED ───────────────────────────────────────────────────────

    @Test
    @DisplayName("SESSION_EXPIRED with endedAt → publishes SESSION_DURATION_SECONDS")
    void expired_withEndedAt_publishesDuration() {
        Instant start = Instant.now().minusSeconds(300);
        Instant end   = Instant.now();

        consumer.onSessionEvent(event(EventTypes.Session.EXPIRED, start, end));

        verify(publisher).publishRecorded(
                anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.eq("SESSION_DURATION_SECONDS"),
                anyDouble(), anyString());
    }

    // ── SESSION_HEARTBEAT ─────────────────────────────────────────────────────

    @Test
    @DisplayName("SESSION_HEARTBEAT → no usage recorded")
    void heartbeat_noUsage() {
        consumer.onSessionEvent(event(EventTypes.Session.HEARTBEAT, Instant.now(), null));

        verify(publisher, never()).publishRecorded(
                anyString(), anyString(), anyString(), anyString(), anyDouble(), anyString());
    }
}
