package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;
import java.util.Optional;

public record CslEndpointCS(String type, CslCardinalityCS multiplicity,
                            Optional<String> roleName,
                            CslSourceLocationCS location) {
    public CslEndpointCS {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(multiplicity, "multiplicity");
        roleName = Objects.requireNonNull(roleName, "roleName");
        Objects.requireNonNull(location, "location");
    }
}
