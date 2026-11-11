package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

/** Concrete-syntax representation of an OCL invariant embedded in CSL. */
public record CslInvariantCS(String contextType, String name, String expression,
                             CslSourceLocationCS location) {
    public CslInvariantCS {
        Objects.requireNonNull(contextType, "contextType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(location, "location");
    }
}
