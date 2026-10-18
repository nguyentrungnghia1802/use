package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

public record CslGroupMember(String type, CslCardinality multiplicity) {
    public CslGroupMember {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(multiplicity, "multiplicity");
    }
}
