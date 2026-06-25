package com.modus.license.session.scheduler;

import com.modus.license.session.config.SessionProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive background job that removes stale session references from Redis.
 *
 * <p>When a session's data key expires (TTL), the per-tenant active set
 * ({@code session:active:{tenantId}}) still holds the session ID.
 * This scheduler scans those sets and removes any ID whose data key no
 * longer exists, keeping session counts accurate.
 *
 * <p>Disable in tests with {@code session.cleanup.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "session.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String ACTIVE_KEY_PREFIX  = "session:active:";
    private static final String COUNT_KEY_PREFIX   = "session:count:";

    private final ReactiveRedisTemplate<String, String> redis;
    private final Duration interval;

    public SessionCleanupScheduler(ReactiveRedisTemplate<String, String> redis,
                                   SessionProperties props) {
        this.redis    = redis;
        this.interval = Duration.ofSeconds(props.cleanupIntervalSeconds());
    }

    @PostConstruct
    void start() {
        Flux.interval(interval)
                .onBackpressureDrop()
                .flatMap(tick -> cleanupAll(), 1)   // concurrency=1 — no overlapping runs
                .subscribe(
                        removed -> {
                            if (removed > 0) {
                                log.info("Session cleanup: removed {} stale session reference(s)", removed);
                            } else {
                                log.debug("Session cleanup: nothing to clean up");
                            }
                        },
                        error -> log.error("Session cleanup error — scheduler will retry on next tick", error)
                );
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Scans all {@code session:active:*} keys and cleans stale members. */
    private Mono<Long> cleanupAll() {
        return redis.keys(ACTIVE_KEY_PREFIX + "*")
                .flatMap(this::cleanupTenantActiveSet)
                .reduce(0L, Long::sum)
                .doOnError(e -> log.error("Cleanup scan failed", e))
                .onErrorReturn(0L);
    }

    /** For a given tenant active set, removes any member whose data key has expired. */
    private Flux<Long> cleanupTenantActiveSet(String activeKey) {
        String tenantId = activeKey.substring(ACTIVE_KEY_PREFIX.length());

        return redis.opsForSet().members(activeKey)
                .flatMap(sessionId -> removeIfExpired(sessionId, tenantId, activeKey));
    }

    /**
     * Checks whether the session data key still exists. If not, removes the
     * stale member from the active set and decrements the tenant counter.
     *
     * @return {@code 1} if removed, {@code 0} if the session is still live
     */
    private Mono<Long> removeIfExpired(String sessionId, String tenantId, String activeKey) {
        String dataKey = SESSION_KEY_PREFIX + sessionId;

        return redis.hasKey(dataKey)
                .flatMap(alive -> {
                    if (Boolean.TRUE.equals(alive)) {
                        return Mono.just(0L);
                    }

                    log.debug("Removing stale session ref: sessionId={} tenantId={}", sessionId, tenantId);

                    return redis.opsForSet().remove(activeKey, sessionId)
                            .then(decrementSafe(tenantId))
                            .thenReturn(1L)
                            .doOnError(e -> log.error(
                                    "Failed to remove stale session ref: sessionId={} error={}",
                                    sessionId, e.getMessage()));
                })
                .onErrorReturn(0L);
    }

    /** Decrements the tenant session counter, guarding against going below zero. */
    private Mono<Long> decrementSafe(String tenantId) {
        return redis.opsForValue().decrement(COUNT_KEY_PREFIX + tenantId)
                .flatMap(value -> {
                    if (value < 0) {
                        return redis.opsForValue().set(COUNT_KEY_PREFIX + tenantId, "0")
                                .thenReturn(0L);
                    }
                    return Mono.just(value);
                });
    }
}
