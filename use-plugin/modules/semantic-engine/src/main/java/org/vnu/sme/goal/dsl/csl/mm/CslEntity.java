package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CslEntity(String name, Optional<String> specializes, List<CslAttribute> attributes)
        implements CslCardinalityTarget, EntityDefinition {
    public CslEntity {
        Objects.requireNonNull(name, "name"); specializes = Objects.requireNonNull(specializes, "specializes");
        attributes = List.copyOf(attributes);
    }
    public CslEntity(String name, List<CslAttribute> attributes) { this(name, Optional.empty(), attributes); }
    @Override public String id() { return name; }
}
