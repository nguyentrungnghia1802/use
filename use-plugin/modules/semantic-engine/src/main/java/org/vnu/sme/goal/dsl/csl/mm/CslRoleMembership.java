package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

public record CslRoleMembership(String roleName, CslCardinality cardinality) {
    public CslRoleMembership {
        Objects.requireNonNull(roleName, "roleName");
        Objects.requireNonNull(cardinality, "cardinality");
    }
}
