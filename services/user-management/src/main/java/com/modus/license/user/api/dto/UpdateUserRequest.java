package com.modus.license.user.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Patch-style update — null means "leave unchanged".
 * Email is intentionally not updatable here; use a dedicated
 * email-change flow that triggers re-verification.
 */
public record UpdateUserRequest(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName
) {}
