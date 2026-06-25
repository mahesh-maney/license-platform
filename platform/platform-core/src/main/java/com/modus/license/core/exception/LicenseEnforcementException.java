package com.modus.license.core.exception;

public class LicenseEnforcementException extends ModusException {

    public LicenseEnforcementException(ErrorCode errorCode) {
        super(errorCode);
    }

    public LicenseEnforcementException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static LicenseEnforcementException denied(String reason) {
        return new LicenseEnforcementException(ErrorCode.ENFORCEMENT_DENIED, reason);
    }

    public static LicenseEnforcementException timeout() {
        return new LicenseEnforcementException(ErrorCode.ENFORCEMENT_TIMEOUT);
    }

    public static LicenseEnforcementException seatLimitExceeded(String tenantId) {
        return new LicenseEnforcementException(ErrorCode.LICENSE_LIMIT_EXCEEDED,
                "License seat limit exceeded for tenant: " + tenantId);
    }

    public static LicenseEnforcementException sessionLimitExceeded(String tenantId) {
        return new LicenseEnforcementException(ErrorCode.SESSION_LIMIT_EXCEEDED,
                "Concurrent session limit exceeded for tenant: " + tenantId);
    }
}
