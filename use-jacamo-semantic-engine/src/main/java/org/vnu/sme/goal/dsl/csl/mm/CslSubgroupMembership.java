package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

public record CslSubgroupMembership(CslGroup group, CslCardinality cardinality)
        implements CslCardinalityTarget, SubGroup {
    public CslSubgroupMembership {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(cardinality, "cardinality");
    }

    @Override public String id() { return group.name(); }
}
