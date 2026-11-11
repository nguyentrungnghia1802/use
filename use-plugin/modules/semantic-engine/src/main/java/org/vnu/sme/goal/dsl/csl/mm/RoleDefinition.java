package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;

/** YAML-facing role definition name. */
public interface RoleDefinition extends CardinalityTarget {
    String name();
    List<String> parentRoles();
    List<CslAttribute> attributes();
}
