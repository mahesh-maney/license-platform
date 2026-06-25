package com.modus.license.test.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Redis Testcontainer shared across all test classes in the same JVM.
 *
 * Usage — extend {@link com.modus.license.test.base.BaseRedisIntegrationTest} rather
 * than using this class directly.
 */
public final class RedisTestContainer {

    public static final String IMAGE = "redis:7.2-alpine";
    public static final int    PORT  = 6379;

    @SuppressWarnings({"resource", "unchecked"})
    private static final GenericContainer<?> INSTANCE =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(PORT)
                    .withCommand("redis-server", "--requirepass", "test_redis_pass")
                    .withReuse(true);

    static {
        INSTANCE.start();
    }

    private RedisTestContainer() {}

    public static GenericContainer<?> getInstance() { return INSTANCE; }
    public static String getHost()     { return INSTANCE.getHost(); }
    public static int    getMappedPort() { return INSTANCE.getMappedPort(PORT); }
    public static String getPassword() { return "test_redis_pass"; }
}
