package com.modus.license.session.service;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.domain.id.TenantId;
import com.modus.license.core.domain.id.UserId;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.session.api.dto.SessionResponse;
import com.modus.license.session.api.dto.StartSessionRequest;
import com.modus.license.session.config.SessionProperties;
import com.modus.license.session.domain.event.SessionEventPublisher;
import com.modus.license.session.domain.model.SessionRecord;
import com.modus.license.session.domain.repository.SessionRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService")
class SessionServiceTest {

    @Mock SessionRepository   repository;
    @Mock SessionEventPublisher eventPublisher;

    SessionProperties props = new SessionProperties(3600, 60, 3, 30);  // maxConcurrent=3

    SessionService service;

    static final TenantContext CTX = TenantContextTestHelper.defaultContext();
    static final String TENANT_ID  = CTX.tenantId().value().toString();
    static final String USER_ID    = CTX.userId().value().toString();

    @BeforeEach
    void setUp() {
        service = new SessionService(repository, eventPublisher, props);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SessionRecord record(String sessionId) {
        Instant now = Instant.now();
        return new SessionRecord(sessionId, TENANT_ID, USER_ID, null, null, null, now, now);
    }

    private StartSessionRequest request() {
        return new StartSessionRequest(UUID.fromString(USER_ID), null, "192.168.1.1", "TestAgent/1.0");
    }

    // ── startSession ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startSession")
    class StartSession {

        @Test
        @DisplayName("creates a session and returns its response")
        void success() {
            when(repository.getCount(TENANT_ID)).thenReturn(Mono.just(0L));
            when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(repository.incrementCount(TENANT_ID)).thenReturn(Mono.just(1L));

            StepVerifier.create(service.startSession(request(), CTX))
                    .assertNext(resp -> {
                        assertThat(resp.tenantId()).isEqualTo(TENANT_ID);
                        assertThat(resp.userId()).isEqualTo(USER_ID);
                        assertThat(resp.clientIp()).isEqualTo("192.168.1.1");
                        assertThat(resp.sessionId()).isNotBlank();
                    })
                    .verifyComplete();

            verify(eventPublisher).publishStarted(any(SessionRecord.class));
        }

        @Test
        @DisplayName("passes licenseId when provided")
        void withLicenseId() {
            UUID licenseId = UUID.randomUUID();
            StartSessionRequest req = new StartSessionRequest(
                    UUID.fromString(USER_ID), licenseId, null, null);

            when(repository.getCount(TENANT_ID)).thenReturn(Mono.just(0L));
            when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(repository.incrementCount(TENANT_ID)).thenReturn(Mono.just(1L));

            StepVerifier.create(service.startSession(req, CTX))
                    .assertNext(resp -> assertThat(resp.licenseId()).isEqualTo(licenseId.toString()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("rejects when concurrent limit is reached (429)")
        void limitExceeded() {
            when(repository.getCount(TENANT_ID)).thenReturn(Mono.just(3L));  // == maxConcurrentDefault

            StepVerifier.create(service.startSession(request(), CTX))
                    .expectErrorSatisfies(ex -> {
                        assertThat(ex).isInstanceOf(ModusException.class);
                        assertThat(((ModusException) ex).errorCode())
                                .isEqualTo(ErrorCode.SESSION_LIMIT_EXCEEDED);
                    })
                    .verify();

            verify(repository, never()).save(any());
        }
    }

    // ── getSession ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSession")
    class GetSession {

        @Test
        @DisplayName("returns session when found and tenant matches")
        void found() {
            String sessionId = UUID.randomUUID().toString();
            when(repository.findById(sessionId)).thenReturn(Mono.just(record(sessionId)));

            StepVerifier.create(service.getSession(sessionId, CTX))
                    .assertNext(resp -> assertThat(resp.sessionId()).isEqualTo(sessionId))
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns 404 when session not found")
        void notFound() {
            when(repository.findById(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(service.getSession("missing-session", CTX))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("returns 404 when session belongs to different tenant")
        void tenantMismatch() {
            String sessionId = UUID.randomUUID().toString();
            SessionRecord wrongTenant = new SessionRecord(
                    sessionId, "other-tenant", USER_ID, null, null, null, Instant.now(), Instant.now());

            when(repository.findById(sessionId)).thenReturn(Mono.just(wrongTenant));

            StepVerifier.create(service.getSession(sessionId, CTX))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    // ── heartbeat ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("heartbeat")
    class Heartbeat {

        @Test
        @DisplayName("refreshes lastSeenAt and returns updated response")
        void success() {
            String sessionId = UUID.randomUUID().toString();
            SessionRecord existing = record(sessionId);

            when(repository.findById(sessionId)).thenReturn(Mono.just(existing));
            when(repository.refresh(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.heartbeat(sessionId, CTX))
                    .assertNext(resp -> {
                        assertThat(resp.sessionId()).isEqualTo(sessionId);
                        assertThat(resp.lastSeenAt()).isAfterOrEqualTo(existing.lastSeenAt());
                    })
                    .verifyComplete();

            verify(eventPublisher).publishHeartbeat(any(SessionRecord.class));
        }

        @Test
        @DisplayName("returns 404 when session not found")
        void notFound() {
            when(repository.findById(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(service.heartbeat("missing", CTX))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }
    }

    // ── endSession ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("endSession")
    class EndSession {

        @Test
        @DisplayName("deletes session and decrements count")
        void success() {
            String sessionId = UUID.randomUUID().toString();

            when(repository.findById(sessionId)).thenReturn(Mono.just(record(sessionId)));
            when(repository.delete(eq(sessionId), eq(TENANT_ID))).thenReturn(Mono.just(true));
            when(repository.decrementCount(TENANT_ID)).thenReturn(Mono.just(0L));

            StepVerifier.create(service.endSession(sessionId, CTX))
                    .verifyComplete();

            verify(eventPublisher).publishEnded(any(SessionRecord.class), eq("LOGOUT"));
        }
    }

    // ── killSession ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("killSession")
    class KillSession {

        @Test
        @DisplayName("force-terminates session and decrements count")
        void success() {
            String sessionId = UUID.randomUUID().toString();

            when(repository.findById(sessionId)).thenReturn(Mono.just(record(sessionId)));
            when(repository.delete(eq(sessionId), eq(TENANT_ID))).thenReturn(Mono.just(true));
            when(repository.decrementCount(TENANT_ID)).thenReturn(Mono.just(0L));

            StepVerifier.create(service.killSession(sessionId, CTX))
                    .verifyComplete();

            verify(eventPublisher).publishKilled(any(SessionRecord.class), eq("ADMIN_KILL"));
        }
    }

    // ── listActiveSessions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("listActiveSessions")
    class ListActiveSessions {

        @Test
        @DisplayName("returns all sessions for the tenant")
        void returnsSessions() {
            SessionRecord s1 = record(UUID.randomUUID().toString());
            SessionRecord s2 = record(UUID.randomUUID().toString());

            when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Flux.just(s1, s2));

            StepVerifier.create(service.listActiveSessions(CTX))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns empty flux when no active sessions")
        void empty() {
            when(repository.findAllByTenantId(TENANT_ID)).thenReturn(Flux.empty());

            StepVerifier.create(service.listActiveSessions(CTX))
                    .verifyComplete();
        }
    }

    // ── getActiveSessionCount ─────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveSessionCount delegates to repository")
    void getActiveSessionCount() {
        when(repository.getCount(TENANT_ID)).thenReturn(Mono.just(7L));

        StepVerifier.create(service.getActiveSessionCount(TENANT_ID))
                .expectNext(7L)
                .verifyComplete();
    }

    // ── validateSession ───────────────────────────────────────────────────────

    @Test
    @DisplayName("validateSession returns empty when tenant mismatch")
    void validateSessionTenantMismatch() {
        String sessionId = UUID.randomUUID().toString();
        SessionRecord wrongTenant = new SessionRecord(
                sessionId, "other-tenant", USER_ID, null, null, null, Instant.now(), Instant.now());

        when(repository.findById(sessionId)).thenReturn(Mono.just(wrongTenant));

        StepVerifier.create(service.validateSession(sessionId, TENANT_ID))
                .verifyComplete();   // empty — tenant mismatch filtered out
    }
}
