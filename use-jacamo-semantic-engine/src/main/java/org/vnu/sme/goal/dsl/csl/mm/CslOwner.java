package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

/** @deprecated Projection of a Group composition retained for legacy translators. */
@Deprecated(forRemoval = false)
public record CslOwner(String sourceGroup, String target, CslCardinality multiplicity) {
    public CslOwner {
        Objects.requireNonNull(sourceGroup, "sourceGroup");
        Objects.requireNonNull(target, "target"); Objects.requireNonNull(multiplicity, "multiplicity");
    }
}
