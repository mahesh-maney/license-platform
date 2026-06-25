package com.modus.license.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        /** Roles to assign at creation time. May be empty — defaults to no roles. */
        Set<String> roles
) {
    public CreateUserRequest {
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
    }
}
