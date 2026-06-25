package com.modus.license.test.annotation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for all integration tests across Modus services.
 *
 * Activates the "test" Spring profile (loads application-test.yml overrides)
 * and starts the full Spring application context.
 *
 * Use on a test class directly, or as a base for more specific annotations
 * like {@code @PostgresIntegrationTest}.
 *
 * Example:
 * <pre>
 *   {@literal @}IntegrationTest
 *   class TenantServiceIntegrationTest {
 *       {@literal @}Autowired TenantService service;
 *       // ...
 *   }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
