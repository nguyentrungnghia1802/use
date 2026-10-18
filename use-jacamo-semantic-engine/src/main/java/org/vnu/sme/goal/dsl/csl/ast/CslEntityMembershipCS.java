package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

public record CslEntityMembershipCS(String entityName, CslCardinalityCS cardinality,
                                    CslSourceLocationCS location) {
    public CslEntityMembershipCS {
        Objects.requireNonNull(entityName, "entityName");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(location, "location");
    }
}
