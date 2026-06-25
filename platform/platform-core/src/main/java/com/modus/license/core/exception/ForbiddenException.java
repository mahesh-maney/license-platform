package com.modus.license.core.exception;

public class ForbiddenException extends ModusException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ForbiddenException accessDenied() {
        return new ForbiddenException(ErrorCode.ACCESS_DENIED);
    }

    public static ForbiddenException accessDenied(String reason) {
        return new ForbiddenException(ErrorCode.ACCESS_DENIED, reason);
    }

    public static ForbiddenException tenantSuspended(String tenantId) {
        return new ForbiddenException(ErrorCode.TENANT_SUSPENDED,
                "Tenant is suspended: " + tenantId);
    }

    public static ForbiddenException featureDisabled(String featureKey) {
        return new ForbiddenException(ErrorCode.FEATURE_DISABLED,
                "Feature is not enabled for this tenant: " + featureKey);
    }

    public static ForbiddenException entitlementExpired(String entitlementId) {
        return new ForbiddenException(ErrorCode.ENTITLEMENT_EXPIRED,
                "Entitlement has expired: " + entitlementId);
    }
}
