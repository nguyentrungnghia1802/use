package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

public record CslGroupMemberCS(String type, CslCardinalityCS multiplicity,
                               CslSourceLocationCS location) {
    public CslGroupMemberCS {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(multiplicity, "multiplicity");
        Objects.requireNonNull(location, "location");
    }
}
