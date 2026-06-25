package com.modus.license.observability.filter;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.observability.mdc.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter for blocking (JPA/MVC) services.
 *
 * Reads the {@link TenantContext} already set by {@code TenantContextFilter}
 * (platform-security) and writes tenantId, userId, and correlationId into MDC
 * so they appear in every log line for this request thread.
 *
 * Must run AFTER TenantContextFilter (which populates TenantContextHolder).
 * Order: SecurityProperties.DEFAULT_FILTER_ORDER + 2
 */
public class MdcEnrichmentFilter extends OncePerRequestFilter {

    private final String serviceName;
    private final String region;

    public MdcEnrichmentFilter(String serviceName, String region) {
        this.serviceName = serviceName;
        this.region      = region;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            MDC.put(MdcKeys.SERVICE_NAME, serviceName);
            MDC.put(MdcKeys.REGION,       region);

            String correlationId = request.getHeader("X-Correlation-ID");
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(MdcKeys.CORRELATION_ID, correlationId);
            }

            TenantContext ctx = TenantContextHolder.get();
            if (ctx != null) {
                MDC.put(MdcKeys.TENANT_ID, ctx.tenantId().toString());
                MDC.put(MdcKeys.USER_ID,   ctx.userId().toString());
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.TENANT_ID);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.CORRELATION_ID);
            MDC.remove(MdcKeys.SERVICE_NAME);
            MDC.remove(MdcKeys.REGION);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/info");
    }
}
