package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

public record CslCardinalityConstraint(CslCardinalityTargetKind targetKind, CardinalityTarget target,
                                       CslCardinality cardinality) implements CardinalityConstraint {
    public CslCardinalityConstraint {
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(cardinality, "cardinality");
    }

    public String targetName() { return target.id(); }
}
