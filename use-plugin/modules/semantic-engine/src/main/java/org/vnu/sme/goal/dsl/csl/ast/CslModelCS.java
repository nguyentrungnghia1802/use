package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;

public record CslModelCS(String version, String name, List<CslEnumCS> enums,
                         List<CslDataTypeCS> dataTypes,
                         List<CslEntityCS> entities, List<CslRoleCS> roles,
                         List<CslRelationCS> relations, List<CslGroupCS> groups,
                         List<CslCompatibilityCS> compatibilities,
                         List<CslInvariantCS> invariants,
                         CslSourceLocationCS location) {
    public CslModelCS {
        Objects.requireNonNull(version, "version"); Objects.requireNonNull(name, "name");
        enums = List.copyOf(enums); dataTypes = List.copyOf(dataTypes);
        entities = List.copyOf(entities); roles = List.copyOf(roles);
        relations = List.copyOf(relations); groups = List.copyOf(groups);
        compatibilities = List.copyOf(compatibilities); invariants = List.copyOf(invariants);
        Objects.requireNonNull(location, "location");
    }
}
