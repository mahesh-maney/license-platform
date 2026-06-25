package com.modus.license.namedlicense.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignSeatRequest(

        @NotNull(message = "userId is required")
        UUID userId,

        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email
) {}
