package com.modus.license.core.domain.id;

import java.util.Objects;
import java.util.UUID;

public record PlanId(UUID value) {

    public PlanId {
        Objects.requireNonNull(value, "PlanId value must not be null");
    }

    public static PlanId of(UUID value) {
        return new PlanId(value);
    }

    public static PlanId of(String value) {
        return new PlanId(UUID.fromString(value));
    }

    public static PlanId generate() {
        return new PlanId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
