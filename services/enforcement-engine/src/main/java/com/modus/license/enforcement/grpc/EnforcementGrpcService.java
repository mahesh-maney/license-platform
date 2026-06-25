package com.modus.license.enforcement.grpc;

import com.modus.license.enforcement.api.dto.CheckAccessRequest;
import com.modus.license.enforcement.service.EnforcementService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * gRPC server entry point for enforcement checks.
 * Called by API-Gateway, SDK clients, and other internal services.
 */
@GrpcService
public class EnforcementGrpcService extends EnforcementServiceGrpc.EnforcementServiceImplBase {

    private final EnforcementService enforcementService;

    public EnforcementGrpcService(EnforcementService enforcementService) {
        this.enforcementService = enforcementService;
    }

    @Override
    public void checkAccess(com.modus.license.enforcement.grpc.CheckAccessRequest request,
                             StreamObserver<com.modus.license.enforcement.grpc.CheckAccessResponse> responseObserver) {
        CheckAccessRequest dto = new CheckAccessRequest(
                UUID.fromString(request.getUserId()),
                request.getFeatureKey(),
                request.getSessionId().isEmpty() ? null : request.getSessionId()
        );

        enforcementService.checkAccess(request.getTenantId(), dto)
                .subscribe(
                        result -> {
                            responseObserver.onNext(
                                    com.modus.license.enforcement.grpc.CheckAccessResponse.newBuilder()
                                            .setAllowed(result.allowed())
                                            .setDecision(result.decision())
                                            .setDenialReason(result.denialReason() != null ? result.denialReason() : "")
                                            .setEntitlementId(result.entitlementId() != null ? result.entitlementId() : "")
                                            .setResponseTimeMs(result.responseTimeMs())
                                            .setCacheHit(result.cacheHit())
                                            .build()
                            );
                            responseObserver.onCompleted();
                        },
                        responseObserver::onError
                );
    }
}
