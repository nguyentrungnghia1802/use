package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

/** Concrete-syntax declaration of an opaque, named CSL data type. */
public record CslDataTypeCS(String name, CslSourceLocationCS location) {
    public CslDataTypeCS {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }
}
