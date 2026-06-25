package com.modus.license.entitlement.domain.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.entitlement.api.dto.EntitlementResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Caches the active entitlement per tenant in Redis for fast enforcement lookups.
 *
 * Key format: {@code entitlement:active:{tenantId}}
 * Value: JSON-serialized {@link EntitlementResponse}
 */
@Service
public class EntitlementCacheService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementCacheService.class);
    private static final String KEY_PREFIX = "entitlement:active:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${cache.entitlement-ttl-seconds:300}")
    private long ttlSeconds;

    public EntitlementCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void cache(UUID tenantId, EntitlementResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(key(tenantId), json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Failed to cache entitlement for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    public Optional<EntitlementResponse> get(UUID tenantId) {
        try {
            String json = redis.opsForValue().get(key(tenantId));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, EntitlementResponse.class));
        } catch (Exception e) {
            log.warn("Failed to read cached entitlement for tenant {}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    public void evict(UUID tenantId) {
        redis.delete(key(tenantId));
        log.debug("Evicted entitlement cache for tenant {}", tenantId);
    }

    private String key(UUID tenantId) {
        return KEY_PREFIX + tenantId;
    }
}
