package com.modus.license.security.filter;

import com.modus.license.core.context.TenantContext;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.core.exception.TenantContextException;
import com.modus.license.security.jwt.JwtClaimsExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter for blocking (JPA/MVC) services (tenant-management, subscription-plan, etc.).
 *
 * Runs after Spring Security has populated the {@link SecurityContextHolder}.
 * Extracts the tenant context from the validated JWT and sets it on
 * {@link TenantContextHolder} for the duration of the request.
 *
 * Always clears the context in finally to prevent leaks across thread-pool reuse.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private final JwtClaimsExtractor extractor;

    public TenantContextFilter(JwtClaimsExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                TenantContext ctx = extractor.extract(jwtAuth.getToken());
                TenantContextHolder.set(ctx);
            }
            filterChain.doFilter(request, response);
        } catch (TenantContextException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Skip tenant context for public endpoints
        return path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.startsWith("/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
