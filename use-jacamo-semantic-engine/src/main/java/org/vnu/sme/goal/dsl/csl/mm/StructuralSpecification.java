package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;
import java.util.Objects;

/** The single CSL root class from csl.yaml; rootGroup is exactly one. */
public class StructuralSpecification {
    private final String id;
    private final List<CslEnum> enums;
    private final List<CslRole> roles;
    private final List<CslEntity> entities;
    private final List<CslRoleInheritance> inheritances;
    private final CslGroup rootGroup;

    public StructuralSpecification(String id, List<CslEnum> enums, List<CslRole> roles,
                                   List<CslEntity> entities, List<CslRoleInheritance> inheritances,
                                   CslGroup rootGroup) {
        this.id = Objects.requireNonNull(id, "id");
        this.enums = List.copyOf(enums);
        this.roles = List.copyOf(roles);
        this.entities = List.copyOf(entities);
        this.inheritances = List.copyOf(inheritances);
        this.rootGroup = Objects.requireNonNull(rootGroup, "rootGroup");
    }

    public String id() { return id; }
    public List<CslEnum> enums() { return enums; }
    public List<CslRole> roles() { return roles; }
    public List<CslEntity> entities() { return entities; }
    public List<CslRoleInheritance> inheritances() { return inheritances; }
    public CslGroup rootGroup() { return rootGroup; }
}
