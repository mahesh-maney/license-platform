package com.modus.license.core.domain.id;

import java.util.Objects;
import java.util.UUID;

public record EntitlementId(UUID value) {

    public EntitlementId {
        Objects.requireNonNull(value, "EntitlementId value must not be null");
    }

    public static EntitlementId of(UUID value) {
        return new EntitlementId(value);
    }

    public static EntitlementId of(String value) {
        return new EntitlementId(UUID.fromString(value));
    }

    public static EntitlementId generate() {
        return new EntitlementId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
