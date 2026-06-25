package com.modus.license.test.containers;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Kafka Testcontainer shared across all test classes in the same JVM.
 *
 * Uses the Confluent platform image so it is compatible with the same version
 * used in docker-compose.yml (7.6.1).
 *
 * For Avro serialization in tests, services should configure:
 * {@code spring.kafka.properties.schema.registry.url=mock://test}
 * This activates MockSchemaRegistryClient without requiring a real registry.
 *
 * Usage — extend {@link com.modus.license.test.base.BaseKafkaIntegrationTest} rather
 * than using this class directly.
 */
public final class KafkaTestContainer {

    public static final String IMAGE = "confluentinc/cp-kafka:7.6.1";

    @SuppressWarnings("resource")
    private static final KafkaContainer INSTANCE =
            new KafkaContainer(DockerImageName.parse(IMAGE))
                    .withReuse(true);

    static {
        INSTANCE.start();
    }

    private KafkaTestContainer() {}

    public static KafkaContainer getInstance()        { return INSTANCE; }
    public static String          getBootstrapServers() { return INSTANCE.getBootstrapServers(); }
}
