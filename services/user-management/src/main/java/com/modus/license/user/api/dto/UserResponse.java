package com.modus.license.user.api.dto;

import com.modus.license.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {}
