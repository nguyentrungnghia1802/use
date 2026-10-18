package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Arrays;
import java.util.Optional;

public enum CslPrimitiveType implements CslDataType {
    STRING("String"),
    INTEGER("Integer"),
    REAL("Real"),
    BOOLEAN("Boolean");

    private final String sourceName;

    CslPrimitiveType(String sourceName) {
        this.sourceName = sourceName;
    }

    @Override
    public String toString() {
        return sourceName;
    }

    public String sourceName() {
        return sourceName;
    }

    public static Optional<CslPrimitiveType> fromSource(String source) {
        return Arrays.stream(values()).filter(value -> value.sourceName.equals(source)).findFirst();
    }
}
