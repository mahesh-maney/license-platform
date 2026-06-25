package com.modus.license.enforcement.domain.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.enforcement.domain.model.CachedEntitlement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive Redis cache for per-tenant entitlement data.
 * Key: {@code enforcement:entitlement:{tenantId}}
 */
@Component
public class EntitlementCacheService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementCacheService.class);
    private static final String KEY_PREFIX = "enforcement:entitlement:";

    private final ReactiveRedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public EntitlementCacheService(ReactiveRedisTemplate<String, String> redis,
                                    ObjectMapper enforcementObjectMapper,
                                    @Value("${cache.entitlement-ttl-seconds:300}") long ttlSeconds) {
        this.redis        = redis;
        this.objectMapper = enforcementObjectMapper;
        this.ttl          = Duration.ofSeconds(ttlSeconds);
    }

    public Mono<CachedEntitlement> get(String tenantId) {
        return redis.opsForValue()
                .get(KEY_PREFIX + tenantId)
                .mapNotNull(json -> {
                    try {
                        return objectMapper.readValue(json, CachedEntitlement.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialise cached entitlement for tenant={}: {}",
                                tenantId, e.getMessage());
                        return null;
                    }
                });
    }

    public Mono<Void> put(CachedEntitlement entitlement) {
        try {
            String json = objectMapper.writeValueAsString(entitlement);
            return redis.opsForValue()
                    .set(KEY_PREFIX + entitlement.tenantId(), json, ttl)
                    .doOnSuccess(ok -> log.debug("Cached entitlement for tenant={}", entitlement.tenantId()))
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to serialise CachedEntitlement", e));
        }
    }

    public Mono<Void> evict(String tenantId) {
        return redis.delete(KEY_PREFIX + tenantId)
                .doOnSuccess(n -> log.debug("Evicted entitlement cache for tenant={}", tenantId))
                .then();
    }
}
