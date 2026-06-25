package com.modus.license.test.base;

import com.modus.license.test.annotation.IntegrationTest;
import com.modus.license.test.containers.KafkaTestContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that produce or consume Kafka events.
 *
 * Starts a singleton Confluent Kafka container (once per JVM).
 *
 * Avro serialization in tests: configures {@code mock://test} as the Schema Registry
 * URL. The Confluent KafkaAvroSerializer/Deserializer treats any URL starting with
 * {@code mock://} as a trigger to use {@code MockSchemaRegistryClient}, which holds
 * schemas in memory without requiring a running registry.
 *
 * Example:
 * <pre>
 *   {@literal @}IntegrationTest
 *   class TenantEventProducerTest extends BaseKafkaIntegrationTest {
 *       {@literal @}Autowired KafkaTemplate{@literal <}String, Object{@literal >} kafkaTemplate;
 *       {@literal @}Autowired KafkaTestEventConsumer consumer;
 *       // ...
 *   }
 * </pre>
 */
@IntegrationTest
public abstract class BaseKafkaIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainer::getBootstrapServers);

        // Use MockSchemaRegistryClient — no real Schema Registry needed in tests
        registry.add("spring.kafka.properties.schema.registry.url", () -> "mock://test");

        // Consumer settings for tests
        registry.add("spring.kafka.consumer.auto-offset-reset",  () -> "earliest");
        registry.add("spring.kafka.consumer.enable-auto-commit", () -> "false");

        // Disable SASL for tests
        registry.add("spring.kafka.properties.security.protocol", () -> "PLAINTEXT");
    }
}
