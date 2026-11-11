package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

/** CSL-specific relation; deliberately not a MOISE Link/RoleRelation. */
public record CslRoleEntityRelation(String name, CslRole sourceRole, CslEntity targetEntity,
                                    CslRoleEntityRelationType type, CslScope scope,
                                    boolean extendsSubgroups) implements RoleEntityRelation {
    public CslRoleEntityRelation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceRole, "sourceRole");
        Objects.requireNonNull(targetEntity, "targetEntity");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
    }
}
