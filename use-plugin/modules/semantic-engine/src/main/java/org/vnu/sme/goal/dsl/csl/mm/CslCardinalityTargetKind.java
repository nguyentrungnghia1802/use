package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Arrays;
import java.util.Optional;

public enum CslCardinalityTargetKind {
    ROLE("role"),
    ENTITY("entity"),
    SUBGROUP("subgroup");

    private final String sourceName;

    CslCardinalityTargetKind(String sourceName) {
        this.sourceName = sourceName;
    }

    public String sourceName() {
        return sourceName;
    }

    public static Optional<CslCardinalityTargetKind> fromSource(String source) {
        return Arrays.stream(values()).filter(value -> value.sourceName.equals(source)).findFirst();
    }
}
