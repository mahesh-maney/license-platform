package com.modus.license.core.exception;

public enum ErrorCode {

    // --- Tenant ---
    TENANT_NOT_FOUND        (404, "Tenant not found"),
    TENANT_ALREADY_EXISTS   (409, "Tenant already exists"),
    TENANT_SUSPENDED        (403, "Tenant is suspended"),
    TENANT_INACTIVE         (403, "Tenant is inactive"),
    TENANT_CONTEXT_MISSING  (401, "Tenant context is missing from the request"),

    // --- User ---
    USER_NOT_FOUND          (404, "User not found"),
    USER_ALREADY_EXISTS     (409, "User already exists"),

    // --- Subscription ---
    SUBSCRIPTION_NOT_FOUND  (404, "Subscription not found"),
    SUBSCRIPTION_CONFLICT   (409, "Subscription conflict"),

    // --- Plan ---
    PLAN_NOT_FOUND          (404, "Subscription plan not found"),
    PLAN_ALREADY_EXISTS     (409, "Subscription plan already exists"),

    // --- Entitlement ---
    ENTITLEMENT_NOT_FOUND   (404, "Entitlement not found"),
    ENTITLEMENT_EXPIRED     (403, "Entitlement has expired"),
    ENTITLEMENT_SUSPENDED   (403, "Entitlement is suspended"),
    ENTITLEMENT_REVOKED     (403, "Entitlement has been revoked"),

    // --- Feature ---
    FEATURE_NOT_FOUND       (404, "Feature not found"),
    FEATURE_DISABLED        (403, "Feature is not enabled for this tenant"),

    // --- License ---
    LICENSE_NOT_FOUND       (404, "License not found"),
    LICENSE_LIMIT_EXCEEDED  (429, "License seat limit has been reached"),
    LICENSE_ALREADY_ASSIGNED(409, "License is already assigned to this user"),
    LICENSE_NOT_ASSIGNED    (404, "License is not assigned to this user"),

    // --- Session ---
    SESSION_NOT_FOUND       (404, "Session not found"),
    SESSION_LIMIT_EXCEEDED  (429, "Concurrent session limit has been reached"),
    SESSION_EXPIRED         (403, "Session has expired"),

    // --- Enforcement ---
    ENFORCEMENT_DENIED      (403, "License enforcement check denied access"),
    ENFORCEMENT_TIMEOUT     (503, "Enforcement decision timed out"),

    // --- Common ---
    VALIDATION_ERROR        (400, "Request validation failed"),
    ACCESS_DENIED           (403, "Access denied"),
    CONFLICT                (409, "Resource conflict"),
    INTERNAL_ERROR          (500, "An internal error occurred");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
