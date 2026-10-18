package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CslEntityCS(String name, Optional<String> specializes,
                          List<CslAttributeCS> attributes, CslSourceLocationCS location) {
    public CslEntityCS {
        Objects.requireNonNull(name, "name");
        specializes = Objects.requireNonNull(specializes, "specializes");
        attributes = List.copyOf(attributes);
        Objects.requireNonNull(location, "location");
    }
}
