package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;
import java.util.Optional;

public record CslEndpoint(String type, CslCardinality multiplicity, Optional<String> roleName) {
    public CslEndpoint {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(multiplicity, "multiplicity");
        roleName = Objects.requireNonNull(roleName, "roleName");
    }
}
