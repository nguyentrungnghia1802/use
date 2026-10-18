package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

/** MOISE role inheritance edge (child -> parent). */
public record CslRoleInheritance(String child, String parent) implements RoleInheritance {
    public CslRoleInheritance {
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(parent, "parent");
    }
}
