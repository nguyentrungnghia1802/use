package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Arrays;
import java.util.Optional;

public enum CslCompatibilityType {
    COMPATIBLE("compatible"),
    INCOMPATIBLE("incompatible");

    private final String sourceName;

    CslCompatibilityType(String sourceName) {
        this.sourceName = sourceName;
    }

    public String sourceName() {
        return sourceName;
    }

    public static Optional<CslCompatibilityType> fromSource(String source) {
        return Arrays.stream(values()).filter(value -> value.sourceName.equals(source)).findFirst();
    }
}
