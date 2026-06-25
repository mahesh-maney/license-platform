package com.modus.license.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit trail emission.
 *
 * The {@link com.modus.license.audit.aspect.AuditableAspect} intercepts annotated
 * methods, builds an {@link com.modus.license.events.audit.AuditEvent}, and publishes
 * it to the {@code modus.audit.events} Kafka topic after the method succeeds or fails.
 *
 * Works for both blocking (JPA/servlet) and reactive (Mono/Flux) return types.
 *
 * Example:
 * <pre>
 *   {@literal @}Auditable(
 *       action       = AuditAction.CREATE,
 *       resourceType = "TENANT",
 *       resourceIdExpression = "#result.id.toString()"
 *   )
 *   public Tenant createTenant(CreateTenantCommand cmd) { ... }
 *
 *   {@literal @}Auditable(
 *       action       = AuditAction.DELETE,
 *       resourceType = "USER",
 *       resourceIdExpression = "#args[0].toString()"
 *   )
 *   public Mono{@literal <}Void{@literal >} deleteUser(UUID userId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** The action performed by this method. */
    AuditAction action();

    /**
     * Domain resource type, e.g. {@code "TENANT"}, {@code "USER"}, {@code "SUBSCRIPTION"}.
     * Stored as-is in the audit event's {@code resourceType} field.
     */
    String resourceType();

    /**
     * Optional SpEL expression to extract the resource ID from the method's return
     * value or arguments.
     *
     * <ul>
     *   <li>{@code "#result.id.toString()"} — uses the return value</li>
     *   <li>{@code "#args[0].toString()"} — uses the first argument</li>
     *   <li>{@code "#args[0].tenantId().toString()"} — navigates into the first argument</li>
     * </ul>
     *
     * Leave empty to omit the resource ID from the audit event.
     */
    String resourceIdExpression() default "";

    /**
     * Name of the service emitting the audit event.
     * Defaults to {@code spring.application.name} from the application context.
     * Override only when the audit must attribute a different service name.
     */
    String serviceName() default "";
}
