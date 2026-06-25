package com.modus.license.reporting.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Request body for {@code POST /api/v1/reports/export}.
 *
 * @param reportType one of {@code USAGE}, {@code ENTITLEMENT}, {@code SUBSCRIPTION}
 * @param from       window start (optional; defaults to epoch)
 * @param to         window end   (optional; defaults to now)
 */
public record ExportRequest(
        @NotBlank String  reportType,
                  Instant from,
                  Instant to
) {}
