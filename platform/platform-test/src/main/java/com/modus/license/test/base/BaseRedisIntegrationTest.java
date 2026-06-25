package com.modus.license.test.base;

import com.modus.license.test.annotation.IntegrationTest;
import com.modus.license.test.containers.RedisTestContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that require Redis (session, enforcement-engine,
 * entitlement cache, etc.).
 *
 * Starts a singleton Redis container (once per JVM) and wires its connection
 * details into Spring's {@code spring.data.redis.*} properties.
 *
 * Test isolation: Redis is not automatically flushed between tests.
 * Use {@code @BeforeEach} with {@code redisTemplate.getConnectionFactory()
 * .getConnection().serverCommands().flushAll()} if needed.
 */
@IntegrationTest
public abstract class BaseRedisIntegrationTest {

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host",     RedisTestContainer::getHost);
        registry.add("spring.data.redis.port",     RedisTestContainer::getMappedPort);
        registry.add("spring.data.redis.password", RedisTestContainer::getPassword);
        registry.add("spring.data.redis.ssl.enabled", () -> "false");
    }
}
