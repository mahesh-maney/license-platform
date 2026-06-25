package com.modus.license.core.exception;

public class TenantContextException extends ModusException {

    public TenantContextException() {
        super(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    public TenantContextException(String message) {
        super(ErrorCode.TENANT_CONTEXT_MISSING, message);
    }

    public static TenantContextException missing() {
        return new TenantContextException(
                "Tenant context is missing. Ensure the JWT contains a valid tenant_id claim."
        );
    }
}
