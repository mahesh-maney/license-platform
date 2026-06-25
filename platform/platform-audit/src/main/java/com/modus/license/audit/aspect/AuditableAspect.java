package com.modus.license.audit.aspect;

import com.modus.license.audit.annotation.Auditable;
import com.modus.license.audit.publisher.AuditEventBuilder;
import com.modus.license.audit.publisher.AuditEventPublisher;
import com.modus.license.core.context.TenantContext;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.events.audit.AuditEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AOP aspect that intercepts {@link Auditable}-annotated methods and emits
 * an {@link AuditEvent} to Kafka upon success or failure.
 *
 * Handles both reactive (Mono/Flux) and blocking return types:
 * <ul>
 *   <li>Mono — chains audit publication via doOnSuccess/doOnError within the pipeline</li>
 *   <li>Flux — chains audit publication via doOnComplete/doOnError</li>
 *   <li>Blocking — publishes synchronously after method returns or throws</li>
 * </ul>
 *
 * Audit failures are always swallowed — they must never break the main flow.
 * TenantContext must be set (TenantContextHolder for blocking, Reactor Context for reactive)
 * for the audit event to include tenant/user information.
 */
@Aspect
public class AuditableAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditableAspect.class);

    private final AuditEventPublisher publisher;
    private final String              defaultServiceName;
    private final ExpressionParser    spelParser = new SpelExpressionParser();

    public AuditableAspect(AuditEventPublisher publisher, String defaultServiceName) {
        this.publisher          = publisher;
        this.defaultServiceName = defaultServiceName;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        String serviceName = auditable.serviceName().isBlank()
                ? defaultServiceName
                : auditable.serviceName();

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            // Blocking method threw — publish failure and re-throw
            publishBlockingFailure(auditable, serviceName, ex);
            throw ex;
        }

        if (result instanceof Mono<?> mono) {
            return auditMono(mono, pjp, auditable, serviceName);
        }

        if (result instanceof Flux<?> flux) {
            return auditFlux(flux, auditable, serviceName);
        }

        // Blocking success
        publishBlockingSuccess(auditable, serviceName, result, pjp.getArgs());
        return result;
    }

    // ── Reactive paths ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> Mono<T> auditMono(Mono<T> mono, ProceedingJoinPoint pjp,
                                   Auditable auditable, String serviceName) {
        return mono
                .flatMap(result ->
                    Mono.deferContextual(ctx -> {
                        TenantContext tenantCtx = ctx.hasKey(TenantContext.REACTOR_CONTEXT_KEY)
                                ? ctx.get(TenantContext.REACTOR_CONTEXT_KEY)
                                : null;
                        if (tenantCtx != null) {
                            String resourceId = evalResourceId(auditable.resourceIdExpression(), result, pjp.getArgs());
                            AuditEvent event  = AuditEventBuilder.success(
                                    tenantCtx, auditable.action(),
                                    auditable.resourceType(), resourceId, serviceName);
                            publisher.publishAsync(event).subscribe();
                        }
                        return Mono.just(result);
                    })
                )
                .doOnError(ex ->
                    Mono.deferContextual(ctx -> {
                        TenantContext tenantCtx = ctx.hasKey(TenantContext.REACTOR_CONTEXT_KEY)
                                ? ctx.get(TenantContext.REACTOR_CONTEXT_KEY)
                                : null;
                        if (tenantCtx != null) {
                            AuditEvent event = AuditEventBuilder.failure(
                                    tenantCtx, auditable.action(),
                                    auditable.resourceType(), null,
                                    ex.getMessage(), serviceName);
                            publisher.publishAsync(event).subscribe();
                        }
                        return Mono.empty();
                    }).subscribe()
                );
    }

    @SuppressWarnings("unchecked")
    private <T> Flux<T> auditFlux(Flux<T> flux, Auditable auditable, String serviceName) {
        return flux
                .doOnComplete(() ->
                    Mono.deferContextual(ctx -> {
                        TenantContext tenantCtx = ctx.hasKey(TenantContext.REACTOR_CONTEXT_KEY)
                                ? ctx.get(TenantContext.REACTOR_CONTEXT_KEY)
                                : null;
                        if (tenantCtx != null) {
                            AuditEvent event = AuditEventBuilder.success(
                                    tenantCtx, auditable.action(),
                                    auditable.resourceType(), null, serviceName);
                            publisher.publishAsync(event).subscribe();
                        }
                        return Mono.empty();
                    }).subscribe()
                )
                .doOnError(ex ->
                    Mono.deferContextual(ctx -> {
                        TenantContext tenantCtx = ctx.hasKey(TenantContext.REACTOR_CONTEXT_KEY)
                                ? ctx.get(TenantContext.REACTOR_CONTEXT_KEY)
                                : null;
                        if (tenantCtx != null) {
                            AuditEvent event = AuditEventBuilder.failure(
                                    tenantCtx, auditable.action(),
                                    auditable.resourceType(), null,
                                    ex.getMessage(), serviceName);
                            publisher.publishAsync(event).subscribe();
                        }
                        return Mono.empty();
                    }).subscribe()
                );
    }

    // ── Blocking paths ────────────────────────────────────────────────────────

    private void publishBlockingSuccess(Auditable auditable, String serviceName,
                                         Object result, Object[] args) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) return;
        try {
            String resourceId = evalResourceId(auditable.resourceIdExpression(), result, args);
            AuditEvent event  = AuditEventBuilder.success(
                    ctx, auditable.action(), auditable.resourceType(), resourceId, serviceName);
            publisher.publish(event);
        } catch (Exception e) {
            log.warn("Audit aspect failed to build/publish success event: {}", e.getMessage());
        }
    }

    private void publishBlockingFailure(Auditable auditable, String serviceName, Throwable ex) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) return;
        try {
            AuditEvent event = AuditEventBuilder.failure(
                    ctx, auditable.action(), auditable.resourceType(),
                    null, ex.getMessage(), serviceName);
            publisher.publish(event);
        } catch (Exception e) {
            log.warn("Audit aspect failed to build/publish failure event: {}", e.getMessage());
        }
    }

    // ── SpEL resource ID extraction ───────────────────────────────────────────

    private String evalResourceId(String expression, Object result, Object[] args) {
        if (expression == null || expression.isBlank()) return null;
        try {
            EvaluationContext ctx = new StandardEvaluationContext();
            ctx.setVariable("result", result);
            ctx.setVariable("args", args);
            Expression expr = spelParser.parseExpression(expression);
            Object value    = expr.getValue(ctx);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Could not evaluate resourceIdExpression '{}': {}", expression, e.getMessage());
            return null;
        }
    }
}
