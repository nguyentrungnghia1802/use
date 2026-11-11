package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;
import java.util.Objects;

public record CslRole(String name, List<String> parentRoles, List<CslAttribute> attributes)
        implements CslCardinalityTarget, RoleDefinition {
    public CslRole {
        Objects.requireNonNull(name, "name");
        parentRoles = List.copyOf(parentRoles);
        attributes = List.copyOf(attributes);
    }

    @Override public String id() { return name; }

    /**
     * Compatibility shim for downstream modules awaiting migration from legacy
     * abstract roles. CSL Roles are always concrete.
     */
    @Deprecated(forRemoval = true)
    public boolean isAbstract() { return false; }
}
