package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;
import java.util.Optional;

public record CslAttributeCS(String name, String typeName, boolean optional,
                             boolean explicitRequired, boolean mutable,
                             Optional<String> defaultValue, CslSourceLocationCS location) {
    public CslAttributeCS {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeName, "typeName");
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(location, "location");
    }
}
