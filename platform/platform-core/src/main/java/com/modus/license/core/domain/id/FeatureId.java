package com.modus.license.core.domain.id;

import java.util.Objects;
import java.util.UUID;

public record FeatureId(UUID value) {

    public FeatureId {
        Objects.requireNonNull(value, "FeatureId value must not be null");
    }

    public static FeatureId of(UUID value) {
        return new FeatureId(value);
    }

    public static FeatureId of(String value) {
        return new FeatureId(UUID.fromString(value));
    }

    public static FeatureId generate() {
        return new FeatureId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
