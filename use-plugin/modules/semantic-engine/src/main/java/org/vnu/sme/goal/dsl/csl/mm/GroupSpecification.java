package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;

/** YAML GroupSpecification role/entity/subgroup container. */
public interface GroupSpecification {
    String name();
    List<CslRoleMembership> roles();
    List<CslEntityMembership> entities();
    List<CslSubgroupMembership> subgroups();
}
