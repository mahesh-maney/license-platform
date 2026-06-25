package com.modus.license.namedlicense.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferSeatRequest(

        @NotNull(message = "fromUserId is required")
        UUID fromUserId,

        @NotNull(message = "toUserId is required")
        UUID toUserId,

        @NotBlank(message = "toUserEmail is required")
        @Email(message = "toUserEmail must be valid")
        String toUserEmail
) {}
