package com.modus.license.core.web;

import java.util.List;

/**
 * Generic paginated response envelope.
 *
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 100,
 *   "totalPages": 5,
 *   "last": false
 * }
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        boolean last = page >= totalPages - 1;
        return new PageResponse<>(content, page, size, totalElements, totalPages, last);
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, true);
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
}
