package com.modus.license.test.base;

import com.modus.license.test.annotation.IntegrationTest;
import com.modus.license.test.containers.PostgresTestContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that require a PostgreSQL database.
 *
 * Starts a singleton container (once per JVM) and wires its URL into the
 * Spring context via {@code @DynamicPropertySource}.
 *
 * Covers both JPA ({@code spring.datasource.*}) and R2DBC ({@code spring.r2dbc.*})
 * property namespaces so both types of service can extend this class.
 *
 * Test isolation: Flyway migrations run once. Use {@code @Transactional} on each
 * test method for automatic rollback, or truncate tables in {@code @BeforeEach}.
 *
 * Example:
 * <pre>
 *   {@literal @}IntegrationTest
 *   class TenantRepositoryTest extends BasePostgresIntegrationTest {
 *       {@literal @}Autowired TenantRepository repo;
 *
 *       {@literal @}Test
 *       {@literal @}Transactional
 *       void shouldPersistTenant() { ... }
 *   }
 * </pre>
 */
@IntegrationTest
public abstract class BasePostgresIntegrationTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        // JPA / JDBC properties
        registry.add("spring.datasource.url",           PostgresTestContainer::getJdbcUrl);
        registry.add("spring.datasource.username",      PostgresTestContainer::getUsername);
        registry.add("spring.datasource.password",      PostgresTestContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // R2DBC properties (for reactive services)
        registry.add("spring.r2dbc.url",      PostgresTestContainer::getR2dbcUrl);
        registry.add("spring.r2dbc.username", PostgresTestContainer::getUsername);
        registry.add("spring.r2dbc.password", PostgresTestContainer::getPassword);

        // Always run Flyway in tests
        registry.add("spring.flyway.enabled",             () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",     () -> "validate");
    }
}
