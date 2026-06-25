package com.modus.license.core.domain.id;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "UserId value must not be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
