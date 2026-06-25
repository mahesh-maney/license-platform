package com.modus.license.core.exception;

import java.util.Collections;
import java.util.List;

public class ValidationException extends ModusException {

    private final List<FieldError> fieldErrors;

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = Collections.emptyList();
    }

    public ValidationException(List<FieldError> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR, "Request validation failed");
        this.fieldErrors = Collections.unmodifiableList(fieldErrors);
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String message) {
        public static FieldError of(String field, String message) {
            return new FieldError(field, message);
        }
    }
}
