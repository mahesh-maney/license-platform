package com.modus.license.core.web;

import java.time.Instant;

/**
 * Generic success response envelope for single-resource operations.
 *
 * {
 *   "data": { ... },
 *   "timestamp": "2026-06-24T10:00:00Z"
 * }
 *
 * Used for create/update/get responses where a single resource is returned.
 * List responses use PageResponse. Delete responses return 204 with no body.
 */
public record ApiResponse<T>(
        T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Instant.now());
    }
}
