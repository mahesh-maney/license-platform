package com.modus.license.session.service;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.session.api.dto.SessionResponse;
import com.modus.license.session.api.dto.StartSessionRequest;
import com.modus.license.session.config.SessionProperties;
import com.modus.license.session.domain.event.SessionEventPublisher;
import com.modus.license.session.domain.model.SessionRecord;
import com.modus.license.session.domain.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository repository;
    private final SessionEventPublisher eventPublisher;
    private final SessionProperties props;

    public SessionService(SessionRepository repository,
                          SessionEventPublisher eventPublisher,
                          SessionProperties props) {
        this.repository     = repository;
        this.eventPublisher = eventPublisher;
        this.props          = props;
    }

    /** Start a new session for the authenticated user. */
    public Mono<SessionResponse> startSession(StartSessionRequest request, TenantContext ctx) {
        String tenantId = ctx.tenantId().value().toString();

        return repository.getCount(tenantId)
                .flatMap(currentCount -> {
                    if (currentCount >= props.maxConcurrentDefault()) {
                        return Mono.error(new ModusException(
                                ErrorCode.SESSION_LIMIT_EXCEEDED,
                                "Concurrent session limit of " + props.maxConcurrentDefault()
                                        + " reached for tenant: " + tenantId));
                    }

                    String sessionId = UUID.randomUUID().toString();
                    Instant now      = Instant.now();

                    SessionRecord record = new SessionRecord(
                            sessionId,
                            tenantId,
                            request.userId().toString(),
                            request.licenseId() != null ? request.licenseId().toString() : null,
                            request.clientIp(),
                            request.userAgent(),
                            now,
                            now
                    );

                    return repository.save(record)
                            .flatMap(saved -> repository.incrementCount(tenantId).thenReturn(saved))
                            .doOnSuccess(eventPublisher::publishStarted)
                            .map(this::toResponse);
                });
    }

    /** Retrieve a session by ID. */
    public Mono<SessionResponse> getSession(String sessionId, TenantContext ctx) {
        return requireSession(sessionId, ctx.tenantId().value().toString())
                .map(this::toResponse);
    }

    /** Extend the session TTL and update lastSeenAt. */
    public Mono<SessionResponse> heartbeat(String sessionId, TenantContext ctx) {
        return requireSession(sessionId, ctx.tenantId().value().toString())
                .flatMap(existing -> {
                    SessionRecord updated = new SessionRecord(
                            existing.sessionId(), existing.tenantId(), existing.userId(),
                            existing.licenseId(), existing.clientIp(), existing.userAgent(),
                            existing.startedAt(), Instant.now()
                    );
                    return repository.refresh(updated)
                            .doOnSuccess(eventPublisher::publishHeartbeat);
                })
                .map(this::toResponse);
    }

    /** End a session (user logout). */
    public Mono<Void> endSession(String sessionId, TenantContext ctx) {
        String tenantId = ctx.tenantId().value().toString();
        return requireSession(sessionId, tenantId)
                .flatMap(session -> repository.delete(sessionId, tenantId)
                        .then(repository.decrementCount(tenantId))
                        .doOnSuccess(v -> eventPublisher.publishEnded(session, "LOGOUT")))
                .then();
    }

    /** Admin force-kill a session. */
    public Mono<Void> killSession(String sessionId, TenantContext ctx) {
        String tenantId = ctx.tenantId().value().toString();
        return requireSession(sessionId, tenantId)
                .flatMap(session -> repository.delete(sessionId, tenantId)
                        .then(repository.decrementCount(tenantId))
                        .doOnSuccess(v -> eventPublisher.publishKilled(session, "ADMIN_KILL")))
                .then();
    }

    /** List all active sessions for the current tenant. */
    public Flux<SessionResponse> listActiveSessions(TenantContext ctx) {
        return repository.findAllByTenantId(ctx.tenantId().value().toString())
                .map(this::toResponse);
    }

    /** Current active session count for a tenant — used by gRPC service. */
    public Mono<Long> getActiveSessionCount(String tenantId) {
        return repository.getCount(tenantId);
    }

    /** Validate session is live and owned by the given tenant — used by gRPC service. */
    public Mono<SessionRecord> validateSession(String sessionId, String tenantId) {
        return repository.findById(sessionId)
                .filter(s -> tenantId.equals(s.tenantId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<SessionRecord> requireSession(String sessionId, String tenantId) {
        return repository.findById(sessionId)
                .filter(s -> tenantId.equals(s.tenantId()))
                .switchIfEmpty(Mono.error(() -> ResourceNotFoundException.session(sessionId)));
    }

    private SessionResponse toResponse(SessionRecord r) {
        return new SessionResponse(
                r.sessionId(), r.tenantId(), r.userId(),
                r.licenseId(), r.clientIp(), r.userAgent(),
                r.startedAt(), r.lastSeenAt()
        );
    }
}
