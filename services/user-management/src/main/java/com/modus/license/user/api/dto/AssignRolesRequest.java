package com.modus.license.user.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Replaces the full set of roles for a user.
 * An empty set removes all roles.
 */
public record AssignRolesRequest(

        @NotNull
        Set<String> roles
) {
    public AssignRolesRequest {
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
    }
}
