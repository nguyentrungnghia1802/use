package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;

public record CslEnumCS(String name, List<String> literals, CslSourceLocationCS location) {
    public CslEnumCS {
        Objects.requireNonNull(name, "name");
        literals = List.copyOf(literals);
        Objects.requireNonNull(location, "location");
    }
}
