package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;

public record CslRoleCS(String name, List<String> parentRoles, List<CslAttributeCS> attributes,
                        CslSourceLocationCS location) {
    public CslRoleCS {
        Objects.requireNonNull(name, "name");
        parentRoles = List.copyOf(parentRoles);
        attributes = List.copyOf(attributes);
        Objects.requireNonNull(location, "location");
    }
}
