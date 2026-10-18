package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

public record CslEntityMembership(String entityName, CslCardinality cardinality) implements GroupEntity {
    public CslEntityMembership {
        Objects.requireNonNull(entityName, "entityName");
        Objects.requireNonNull(cardinality, "cardinality");
    }
}
