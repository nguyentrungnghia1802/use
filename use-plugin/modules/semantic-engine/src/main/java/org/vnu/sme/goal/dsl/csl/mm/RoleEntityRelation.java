package org.vnu.sme.goal.dsl.csl.mm;

/** Dedicated CSL Role -> Entity relation; never a MOISE Link. */
public interface RoleEntityRelation {
    String name();
    CslRole sourceRole();
    CslEntity targetEntity();
    CslRoleEntityRelationType type();
    CslScope scope();
    boolean extendsSubgroups();
}
