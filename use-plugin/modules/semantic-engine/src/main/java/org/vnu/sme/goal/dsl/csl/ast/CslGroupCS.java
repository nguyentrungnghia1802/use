package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CslGroupCS(String name, Optional<String> specializes,
                         List<CslAttributeCS> attributes,
                         List<CslGroupMemberCS> members,
                         List<CslCompatibilityCS> compatibilities,
                         boolean organizationalContext,
                         CslSourceLocationCS location) {
    public CslGroupCS(String name, Optional<String> specializes,
                      List<CslAttributeCS> attributes,
                      List<CslGroupMemberCS> members,
                      List<CslCompatibilityCS> compatibilities,
                      CslSourceLocationCS location) {
        this(name, specializes, attributes, members, compatibilities, false, location);
    }

    public CslGroupCS {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(specializes, "specializes");
        attributes = List.copyOf(attributes);
        members = List.copyOf(members); compatibilities = List.copyOf(compatibilities);
        Objects.requireNonNull(location, "location");
    }
}
