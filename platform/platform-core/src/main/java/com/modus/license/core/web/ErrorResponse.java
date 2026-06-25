package com.modus.license.core.web;

import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ValidationException;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by all services.
 *
 * {
 *   "status": 404,
 *   "code": "TENANT_NOT_FOUND",
 *   "message": "Tenant not found: acme-corp",
 *   "timestamp": "2026-06-24T10:00:00Z",
 *   "fieldErrors": []
 * }
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        Instant timestamp,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(ModusException ex) {
        List<FieldError> fieldErrors = List.of();
        if (ex instanceof ValidationException ve) {
            fieldErrors = ve.fieldErrors().stream()
                    .map(fe -> new FieldError(fe.field(), fe.message()))
                    .toList();
        }
        return new ErrorResponse(
                ex.httpStatus(),
                ex.errorCode().name(),
                ex.getMessage(),
                Instant.now(),
                fieldErrors
        );
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.httpStatus(), code.name(), message, Instant.now(), List.of());
    }

    public static ErrorResponse internal(String message) {
        return new ErrorResponse(500, ErrorCode.INTERNAL_ERROR.name(), message, Instant.now(), List.of());
    }
}
