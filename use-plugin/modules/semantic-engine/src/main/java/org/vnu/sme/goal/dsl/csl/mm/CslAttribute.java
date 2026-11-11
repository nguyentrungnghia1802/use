package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;
import java.util.Optional;

public record CslAttribute(String name, CslDataType type, boolean optional, boolean mutable,
                           Optional<String> defaultValue) implements AttributeDefinition {
    public CslAttribute {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    }
}
