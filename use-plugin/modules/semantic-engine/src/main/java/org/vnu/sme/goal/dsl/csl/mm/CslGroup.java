package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structural context. Legacy {@code group} declarations remain supported. */
public final class CslGroup implements GroupSpecification {
    private final String name;
    private final Optional<String> specializes;
    private final List<CslAttribute> attributes;
    private final List<CslGroupMember> members;
    private final List<CslRoleMembership> roles;
    private final List<CslEntityMembership> entities;
    private final List<CslSubgroupMembership> subgroups;
    private final List<CslCompatibility> compatibilities;
    private final List<CslRoleEntityRelation> roleEntityRelations;
    private final List<CslCardinalityConstraint> cardinalityConstraints;
    private final boolean organizationalContext;

    public CslGroup(String name, Optional<String> specializes, List<CslAttribute> attributes,
                    List<CslGroupMember> members, List<CslRoleMembership> roles,
                    List<CslSubgroupMembership> subgroups,
                    List<CslCompatibility> compatibilities) {
        this(name, specializes, attributes, members, roles, List.of(), subgroups,
                compatibilities, List.of(), List.of(), false);
    }

    /** Compatibility constructor for the former nested-group metamodel. */
    public CslGroup(String name, List<CslRoleMembership> roles, List<CslEntityMembership> entities,
                    List<CslSubgroupMembership> subgroups, List<CslCompatibility> compatibilities,
                    List<CslRoleEntityRelation> roleEntityRelations,
                    List<CslCardinalityConstraint> cardinalityConstraints) {
        java.util.ArrayList<CslGroupMember> projected = new java.util.ArrayList<>();
        roles.forEach(x -> projected.add(new CslGroupMember(x.roleName(), x.cardinality())));
        entities.forEach(x -> projected.add(new CslGroupMember(x.entityName(), x.cardinality())));
        subgroups.forEach(x -> projected.add(new CslGroupMember(x.group().name(), x.cardinality())));
        this.name = Objects.requireNonNull(name, "name");
        this.specializes = Optional.empty();
        this.attributes = List.of();
        this.members = List.copyOf(projected);
        this.roles = List.copyOf(roles);
        this.entities = List.copyOf(entities);
        this.subgroups = List.copyOf(subgroups);
        this.compatibilities = List.copyOf(compatibilities);
        this.roleEntityRelations = List.copyOf(roleEntityRelations);
        this.cardinalityConstraints = List.copyOf(cardinalityConstraints);
        this.organizationalContext = true;
    }

    public CslGroup(String name, Optional<String> specializes, List<CslAttribute> attributes,
                    List<CslGroupMember> members, List<CslRoleMembership> roles,
                    List<CslEntityMembership> entities, List<CslSubgroupMembership> subgroups,
                    List<CslCompatibility> compatibilities,
                    List<CslRoleEntityRelation> roleEntityRelations,
                    List<CslCardinalityConstraint> cardinalityConstraints,
                    boolean organizationalContext) {
        this.name = Objects.requireNonNull(name, "name");
        this.specializes = Objects.requireNonNull(specializes, "specializes");
        this.attributes = List.copyOf(attributes);
        this.members = List.copyOf(members);
        this.roles = List.copyOf(roles);
        this.entities = List.copyOf(entities);
        this.subgroups = List.copyOf(subgroups);
        this.compatibilities = List.copyOf(compatibilities);
        this.roleEntityRelations = List.copyOf(roleEntityRelations);
        this.cardinalityConstraints = List.copyOf(cardinalityConstraints);
        this.organizationalContext = organizationalContext;
    }

    @Override public String name() { return name; }
    public Optional<String> specializes() { return specializes; }
    public List<CslAttribute> attributes() { return attributes; }
    public List<CslGroupMember> members() { return members; }
    @Override public List<CslRoleMembership> roles() { return roles; }
    @Override public List<CslEntityMembership> entities() { return entities; }
    @Override public List<CslSubgroupMembership> subgroups() { return subgroups; }
    public List<CslCompatibility> compatibilities() { return compatibilities; }
    public List<CslRoleEntityRelation> roleEntityRelations() { return roleEntityRelations; }
    public List<CslCardinalityConstraint> cardinalityConstraints() { return cardinalityConstraints; }
    public boolean isOrganizationalContext() { return organizationalContext; }
}
