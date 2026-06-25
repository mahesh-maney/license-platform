package com.modus.license.core.domain.enums;

public enum PlanTier {

    FREE(0),
    STARTER(1),
    PROFESSIONAL(2),
    ENTERPRISE(3),
    CUSTOM(4);

    private final int level;

    PlanTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isAtLeast(PlanTier other) {
        return this.level >= other.level;
    }
}
