package com.modus.license.session.domain.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.session.config.SessionProperties;
import com.modus.license.session.domain.model.SessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis-backed session store.
 *
 * Key layout:
 * <ul>
 *   <li>{@code session:{sessionId}} — JSON blob with TTL</li>
 *   <li>{@code session:count:{tenantId}} — atomic INCR/DECR counter</li>
 *   <li>{@code session:active:{tenantId}} — SET of active sessionIds</li>
 * </ul>
 */
@Repository
public class SessionRepository {

    private static final Logger log = LoggerFactory.getLogger(SessionRepository.class);
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String COUNT_KEY_PREFIX   = "session:count:";
    private static final String ACTIVE_KEY_PREFIX  = "session:active:";

    private final ReactiveRedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public SessionRepository(ReactiveRedisTemplate<String, String> redis,
                              ObjectMapper sessionObjectMapper,
                              SessionProperties props) {
        this.redis        = redis;
        this.objectMapper = sessionObjectMapper;
        this.ttl          = Duration.ofSeconds(props.ttlSeconds());
    }

    public Mono<SessionRecord> save(SessionRecord record) {
        String key = SESSION_KEY_PREFIX + record.sessionId();
        String json = serialize(record);
        return redis.opsForValue().set(key, json, ttl)
                .then(redis.opsForSet().add(ACTIVE_KEY_PREFIX + record.tenantId(), record.sessionId()))
                .thenReturn(record);
    }

    public Mono<Long> incrementCount(String tenantId) {
        return redis.opsForValue().increment(COUNT_KEY_PREFIX + tenantId);
    }

    public Mono<Long> decrementCount(String tenantId) {
        return redis.opsForValue().decrement(COUNT_KEY_PREFIX + tenantId)
                .map(v -> Math.max(0L, v)); // guard against negative on race
    }

    public Mono<Long> getCount(String tenantId) {
        return redis.opsForValue().get(COUNT_KEY_PREFIX + tenantId)
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }

    public Mono<SessionRecord> findById(String sessionId) {
        return redis.opsForValue().get(SESSION_KEY_PREFIX + sessionId)
                .mapNotNull(this::deserialize);
    }

    /**
     * Refreshes the TTL on the session data key (heartbeat).
     * Also updates lastSeenAt with a new record.
     */
    public Mono<SessionRecord> refresh(SessionRecord updated) {
        String key = SESSION_KEY_PREFIX + updated.sessionId();
        String json = serialize(updated);
        return redis.opsForValue().set(key, json, ttl)
                .thenReturn(updated);
    }

    public Mono<Boolean> delete(String sessionId, String tenantId) {
        return redis.delete(SESSION_KEY_PREFIX + sessionId)
                .then(redis.opsForSet().remove(ACTIVE_KEY_PREFIX + tenantId, sessionId))
                .thenReturn(true);
    }

    public Flux<SessionRecord> findAllByTenantId(String tenantId) {
        return redis.opsForSet().members(ACTIVE_KEY_PREFIX + tenantId)
                .flatMap(sessionId -> findById(sessionId)
                        .onErrorResume(e -> {
                            log.warn("Skipping stale session ref: {}", sessionId);
                            return Mono.empty();
                        }))
                .filter(Objects::nonNull);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String serialize(SessionRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise SessionRecord", e);
        }
    }

    private SessionRecord deserialize(String json) {
        try {
            return objectMapper.readValue(json, SessionRecord.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialise SessionRecord from Redis: {}", e.getMessage());
            return null;
        }
    }
}
