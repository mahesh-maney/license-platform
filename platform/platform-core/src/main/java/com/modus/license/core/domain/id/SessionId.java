package com.modus.license.core.domain.id;

import java.util.Objects;
import java.util.UUID;

public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "SessionId value must not be null");
    }

    public static SessionId of(UUID value) {
        return new SessionId(value);
    }

    public static SessionId of(String value) {
        return new SessionId(UUID.fromString(value));
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
