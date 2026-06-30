package com.modus.license.core.exception;

public class ResourceNotFoundException extends ModusException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException tenant(String tenantId) {
        return new ResourceNotFoundException(ErrorCode.TENANT_NOT_FOUND,
                "Tenant not found: " + tenantId);
    }

    public static ResourceNotFoundException user(String userId) {
        return new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                "User not found: " + userId);
    }

    public static ResourceNotFoundException subscription(String subscriptionId) {
        return new ResourceNotFoundException(ErrorCode.SUBSCRIPTION_NOT_FOUND,
                "Subscription not found: " + subscriptionId);
    }

    public static ResourceNotFoundException plan(String planId) {
        return new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND,
                "Plan not found: " + planId);
    }

    public static ResourceNotFoundException entitlement(String entitlementId) {
        return new ResourceNotFoundException(ErrorCode.ENTITLEMENT_NOT_FOUND,
                "Entitlement not found: " + entitlementId);
    }

    public static ResourceNotFoundException feature(String featureKey) {
        return new ResourceNotFoundException(ErrorCode.FEATURE_NOT_FOUND,
                "Feature not found: " + featureKey);
    }

    public static ResourceNotFoundException license(String licenseId) {
        return new ResourceNotFoundException(ErrorCode.LICENSE_NOT_FOUND,
                "License not found: " + licenseId);
    }

    public static ResourceNotFoundException session(String sessionId) {
        return new ResourceNotFoundException(ErrorCode.SESSION_NOT_FOUND,
                "Session not found: " + sessionId);
    }
}
