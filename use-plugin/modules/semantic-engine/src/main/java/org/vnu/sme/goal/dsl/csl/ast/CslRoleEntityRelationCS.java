package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;

/** Concrete-syntax node for the CSL-only Role -> Entity relation. */
public record CslRoleEntityRelationCS(String name, String sourceRole, String targetEntity,
                                      String type, List<CslLinkOptionCS> options,
                                      CslSourceLocationCS location) {
    public CslRoleEntityRelationCS {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceRole, "sourceRole");
        Objects.requireNonNull(targetEntity, "targetEntity");
        Objects.requireNonNull(type, "type");
        options = List.copyOf(options);
        Objects.requireNonNull(location, "location");
    }
}
