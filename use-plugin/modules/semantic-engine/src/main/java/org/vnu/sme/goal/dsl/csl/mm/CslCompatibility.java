package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

public record CslCompatibility(String fromRole, String toRole, CslCompatibilityType type, CslScope scope,
                               boolean extendsSubgroups, boolean bidirectional, String groupName) implements CslRoleRelation, Compatibility {
    public CslCompatibility {
        Objects.requireNonNull(fromRole, "fromRole");
        Objects.requireNonNull(toRole, "toRole");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(groupName, "groupName");
    }
}
