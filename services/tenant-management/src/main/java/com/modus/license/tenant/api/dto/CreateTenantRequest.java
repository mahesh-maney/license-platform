package com.modus.license.tenant.api.dto;

import com.modus.license.core.domain.enums.PlanTier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new tenant.
 *
 * slug:  URL-safe, lowercase, alphanumeric + hyphens, 3–100 chars.
 *        Used as a permanent identifier — chosen at creation, never changed.
 */
public record CreateTenantRequest(

        @NotBlank
        @Pattern(
                regexp = "^[a-z0-9][a-z0-9-]{1,98}[a-z0-9]$",
                message = "slug must be 3–100 lowercase alphanumeric characters and hyphens, no leading/trailing hyphen"
        )
        String slug,

        @NotBlank
        @Size(min = 1, max = 255)
        String name,

        @Size(max = 255)
        String displayName,

        @NotBlank
        @Email
        @Size(max = 255)
        String adminEmail,

        @NotNull
        PlanTier planTier,

        @NotBlank
        @Size(max = 50)
        String region
) {}
