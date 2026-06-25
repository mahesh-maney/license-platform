package com.modus.license.core.exception;

/**
 * Base exception for all Modus platform domain errors.
 *
 * Carries an ErrorCode that maps directly to an HTTP status, so
 * exception handlers in each service can translate to HTTP responses
 * without coupling domain code to web framework types.
 */
public class ModusException extends RuntimeException {

    private final ErrorCode errorCode;

    public ModusException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public ModusException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ModusException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return errorCode.httpStatus();
    }
}
