package org.vnu.sme.goal.dsl.csl.mm;

import java.util.OptionalInt;

public record CslCardinality(int min, OptionalInt max) implements Cardinality {
    public CslCardinality {
        if (min < 0) throw new IllegalArgumentException("minimum cardinality must be non-negative");
        if (max.isPresent() && max.getAsInt() < min) {
            throw new IllegalArgumentException("maximum cardinality must be greater than or equal to minimum");
        }
    }

    public static CslCardinality bounded(int min, int max) {
        return new CslCardinality(min, OptionalInt.of(max));
    }

    public static CslCardinality unlimited(int min) {
        return new CslCardinality(min, OptionalInt.empty());
    }
}
