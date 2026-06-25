package com.modus.license.enforcement.client;

import com.modus.license.session.grpc.ActiveSessionCountRequest;
import com.modus.license.session.grpc.SessionCheckServiceGrpc;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Wraps the generated gRPC blocking stub for the Session service.
 * Calls are offloaded to a bounded-elastic scheduler so reactive pipelines
 * are not blocked.
 */
@Component
public class SessionGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(SessionGrpcClient.class);

    @GrpcClient("session-service")
    private SessionCheckServiceGrpc.SessionCheckServiceBlockingStub sessionStub;

    /**
     * Returns the number of currently active sessions for the given tenant.
     * Returns 0 on any gRPC error (fail-open: let enforcement decide based on other signals).
     */
    public Mono<Long> getActiveSessionCount(String tenantId) {
        return Mono.fromCallable(() -> {
                    ActiveSessionCountRequest request = ActiveSessionCountRequest.newBuilder()
                            .setTenantId(tenantId)
                            .build();
                    return sessionStub.getActiveSessionCount(request).getActiveCount();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(StatusRuntimeException.class, e -> {
                    log.warn("gRPC call to session-service failed for tenant={}: {}", tenantId, e.getStatus());
                    return Mono.just(0L);
                })
                .onErrorResume(e -> {
                    log.error("Unexpected error calling session-service for tenant={}: {}", tenantId, e.getMessage());
                    return Mono.just(0L);
                });
    }
}
