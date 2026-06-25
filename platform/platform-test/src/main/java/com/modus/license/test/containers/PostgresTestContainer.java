package com.modus.license.test.containers;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton PostgreSQL Testcontainer shared across all test classes in the same JVM.
 *
 * Starting a container per test class is expensive (~2s each). By keeping a single
 * static instance, the container starts once and is reused. Test isolation is achieved
 * through database transactions (rolled back per test) or schema-per-test strategies.
 *
 * Usage — extend {@link com.modus.license.test.base.BasePostgresIntegrationTest} rather
 * than using this class directly.
 */
public final class PostgresTestContainer {

    public static final String IMAGE = "postgres:16-alpine";

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("modus_test")
                    .withUsername("modus_test_user")
                    .withPassword("modus_test_pass")
                    .withReuse(true);

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {}

    public static PostgreSQLContainer<?> getInstance() {
        return INSTANCE;
    }

    public static String getJdbcUrl()  { return INSTANCE.getJdbcUrl(); }
    public static String getR2dbcUrl() {
        // Convert jdbc:postgresql://host:port/db → r2dbc:postgresql://host:port/db
        return INSTANCE.getJdbcUrl().replace("jdbc:", "r2dbc:");
    }
    public static String getUsername() { return INSTANCE.getUsername(); }
    public static String getPassword() { return INSTANCE.getPassword(); }
    public static String getHost()     { return INSTANCE.getHost(); }
    public static int    getPort()     { return INSTANCE.getMappedPort(5432); }
}
