package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

public record CslSubgroupMembershipCS(CslGroupCS group, CslCardinalityCS cardinality,
                                      CslSourceLocationCS location) {
    public CslSubgroupMembershipCS {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(location, "location");
    }
}
