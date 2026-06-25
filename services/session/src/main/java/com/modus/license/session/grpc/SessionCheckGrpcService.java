package com.modus.license.session.grpc;

import com.modus.license.session.service.SessionService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC service called by the enforcement-engine for low-latency session checks.
 */
@GrpcService
public class SessionCheckGrpcService extends SessionCheckServiceGrpc.SessionCheckServiceImplBase {

    private final SessionService sessionService;

    public SessionCheckGrpcService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void getActiveSessionCount(ActiveSessionCountRequest request,
                                       StreamObserver<ActiveSessionCountResponse> responseObserver) {
        sessionService.getActiveSessionCount(request.getTenantId())
                .subscribe(
                        count -> {
                            responseObserver.onNext(ActiveSessionCountResponse.newBuilder()
                                    .setTenantId(request.getTenantId())
                                    .setActiveCount(count)
                                    .build());
                            responseObserver.onCompleted();
                        },
                        responseObserver::onError
                );
    }

    @Override
    public void validateSession(ValidateSessionRequest request,
                                 StreamObserver<ValidateSessionResponse> responseObserver) {
        sessionService.validateSession(request.getSessionId(), request.getTenantId())
                .subscribe(
                        session -> {
                            responseObserver.onNext(ValidateSessionResponse.newBuilder()
                                    .setValid(true)
                                    .setUserId(session.userId())
                                    .setLicenseId(session.licenseId() != null ? session.licenseId() : "")
                                    .build());
                            responseObserver.onCompleted();
                        },
                        responseObserver::onError,
                        () -> {
                            // empty — session not found or wrong tenant
                            responseObserver.onNext(ValidateSessionResponse.newBuilder()
                                    .setValid(false)
                                    .setUserId("")
                                    .setLicenseId("")
                                    .build());
                            responseObserver.onCompleted();
                        }
                );
    }
}
