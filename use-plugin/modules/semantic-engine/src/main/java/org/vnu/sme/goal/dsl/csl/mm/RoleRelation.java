package org.vnu.sme.goal.dsl.csl.mm;

/** YAML abstract MOISE RoleRelation. */
public interface RoleRelation {
    String fromRole();
    String toRole();
    CslScope scope();
    boolean extendsSubgroups();
    boolean bidirectional();
}
