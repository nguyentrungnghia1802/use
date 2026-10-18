package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Arrays;
import java.util.Optional;

public enum CslScope {
    INTRA_GROUP("intra-group"),
    INTER_GROUP("inter-group");

    private final String sourceName;

    CslScope(String sourceName) {
        this.sourceName = sourceName;
    }

    public String sourceName() {
        return sourceName;
    }

    public static Optional<CslScope> fromSource(String source) {
        return Arrays.stream(values()).filter(value -> value.sourceName.equals(source)).findFirst();
    }
}
