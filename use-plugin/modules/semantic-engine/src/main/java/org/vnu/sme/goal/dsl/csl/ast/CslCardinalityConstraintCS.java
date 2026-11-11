package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

public record CslCardinalityConstraintCS(String targetKind, String targetName,
                                         CslCardinalityCS cardinality, CslSourceLocationCS location) {
    public CslCardinalityConstraintCS {
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(location, "location");
    }
}
