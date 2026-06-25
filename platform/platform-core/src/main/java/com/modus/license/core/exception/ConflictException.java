package com.modus.license.core.exception;

public class ConflictException extends ModusException {

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ConflictException tenantAlreadyExists(String slug) {
        return new ConflictException(ErrorCode.TENANT_ALREADY_EXISTS,
                "Tenant already exists with slug: " + slug);
    }

    public static ConflictException userAlreadyExists(String email) {
        return new ConflictException(ErrorCode.USER_ALREADY_EXISTS,
                "User already exists with email: " + email);
    }

    public static ConflictException licenseAlreadyAssigned(String userId) {
        return new ConflictException(ErrorCode.LICENSE_ALREADY_ASSIGNED,
                "License already assigned to user: " + userId);
    }
}
