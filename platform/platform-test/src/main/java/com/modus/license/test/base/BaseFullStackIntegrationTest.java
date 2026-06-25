package com.modus.license.test.base;

import com.modus.license.test.annotation.IntegrationTest;
import com.modus.license.test.containers.KafkaTestContainer;
import com.modus.license.test.containers.PostgresTestContainer;
import com.modus.license.test.containers.RedisTestContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for full-stack integration tests requiring PostgreSQL + Kafka + Redis.
 *
 * All three containers are started as singletons (once per JVM) and reused
 * across test classes. Use for tests that exercise the entire vertical slice:
 * HTTP → service → database + cache + event.
 *
 * For tests that only need one or two infrastructure components, prefer the
 * more focused base classes to avoid unnecessary container startup overhead.
 */
@IntegrationTest
public abstract class BaseFullStackIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL — JPA
        registry.add("spring.datasource.url",                PostgresTestContainer::getJdbcUrl);
        registry.add("spring.datasource.username",           PostgresTestContainer::getUsername);
        registry.add("spring.datasource.password",           PostgresTestContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // PostgreSQL — R2DBC
        registry.add("spring.r2dbc.url",      PostgresTestContainer::getR2dbcUrl);
        registry.add("spring.r2dbc.username", PostgresTestContainer::getUsername);
        registry.add("spring.r2dbc.password", PostgresTestContainer::getPassword);
        // Flyway
        registry.add("spring.flyway.enabled",             () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers",                    KafkaTestContainer::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",       () -> "mock://test");
        registry.add("spring.kafka.consumer.auto-offset-reset",           () -> "earliest");
        registry.add("spring.kafka.consumer.enable-auto-commit",          () -> "false");
        registry.add("spring.kafka.properties.security.protocol",         () -> "PLAINTEXT");

        // Redis
        registry.add("spring.data.redis.host",        RedisTestContainer::getHost);
        registry.add("spring.data.redis.port",        RedisTestContainer::getMappedPort);
        registry.add("spring.data.redis.password",    RedisTestContainer::getPassword);
        registry.add("spring.data.redis.ssl.enabled", () -> "false");
    }
}
