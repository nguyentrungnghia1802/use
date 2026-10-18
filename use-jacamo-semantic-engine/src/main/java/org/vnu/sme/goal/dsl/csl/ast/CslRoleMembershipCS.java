package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

public record CslRoleMembershipCS(String roleName, CslCardinalityCS cardinality,
                                  CslSourceLocationCS location) {
    public CslRoleMembershipCS {
        Objects.requireNonNull(roleName, "roleName");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(location, "location");
    }
}
