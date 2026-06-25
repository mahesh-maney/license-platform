package com.modus.license.core.domain.id;

import java.util.Objects;
import java.util.UUID;

public record LicenseId(UUID value) {

    public LicenseId {
        Objects.requireNonNull(value, "LicenseId value must not be null");
    }

    public static LicenseId of(UUID value) {
        return new LicenseId(value);
    }

    public static LicenseId of(String value) {
        return new LicenseId(UUID.fromString(value));
    }

    public static LicenseId generate() {
        return new LicenseId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
