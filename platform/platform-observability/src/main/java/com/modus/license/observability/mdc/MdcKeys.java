package com.modus.license.observability.mdc;

/**
 * Standard MDC key names written by all Modus services.
 *
 * These appear in every log line when using a Logback/Log4j2 pattern that
 * includes %X{key} or a JSON layout that emits MDC fields automatically.
 *
 * Example Logback pattern:
 *   [%X{traceId}] [%X{tenantId}] [%X{serviceName}] %msg%n
 */
public final class MdcKeys {

    private MdcKeys() {}

    /** UUID of the tenant making the request. */
    public static final String TENANT_ID       = "tenantId";

    /** UUID of the acting user. */
    public static final String USER_ID         = "userId";

    /** Distributed trace ID (set automatically by Micrometer Tracing). */
    public static final String TRACE_ID        = "traceId";

    /** Current span ID (set automatically by Micrometer Tracing). */
    public static final String SPAN_ID         = "spanId";

    /** Propagated X-Correlation-ID / B3 header. */
    public static final String CORRELATION_ID  = "correlationId";

    /** spring.application.name of the service emitting the log. */
    public static final String SERVICE_NAME    = "serviceName";

    /** Azure region (platform.region). */
    public static final String REGION          = "region";
}
