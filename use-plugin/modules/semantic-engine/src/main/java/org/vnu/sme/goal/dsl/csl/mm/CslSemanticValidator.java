package org.vnu.sme.goal.dsl.csl.mm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Well-formedness rules for the canonical CSL metamodel. */
public final class CslSemanticValidator {
    private CslSemanticValidator() {}

    public static List<String> validate(CslModel model) {
        List<String> errors = new ArrayList<>();
        Map<String, String> kinds = classifierKinds(model, errors);
        Map<String, CslRole> roles = new LinkedHashMap<>();
        model.roles().forEach(role -> roles.putIfAbsent(role.name(), role));
        Set<String> entities = names(model.entities().stream().map(CslEntity::name).toList());
        Set<String> roleNames = roles.keySet();
        Set<String> groups = names(model.groups().stream().map(CslGroup::name).toList());

        validateGeneralizations(model, kinds, errors);
        validateInheritedProperties(model, errors);
        if (model.isRevisedCore()) {
            validateCore(model, kinds, errors);
            return List.copyOf(errors);
        }
        validateRelations(model.relations(), entities, groups, kinds.keySet(), errors);
        OwnerIndex owners = validateOwners(model.owners(), roleNames, groups, errors);
        validateRoleOwnerScopeMonotonicity(roles, owners, errors);
        validateCompatibility(model.compatibilities(), roles, groups, owners, errors);
        return List.copyOf(errors);
    }

    private static Map<String, String> classifierKinds(CslModel model, List<String> errors) {
        Map<String, String> kinds = new LinkedHashMap<>();
        model.enums().forEach(value -> unique(kinds, value.name(), "Enumeration", errors));
        model.namedDataTypes().forEach(value -> unique(kinds, value.sourceName(), "DataType", errors));
        model.entities().forEach(value -> unique(kinds, value.name(), "Entity", errors));
        model.roles().forEach(value -> unique(kinds, value.name(), "Role", errors));
        model.groups().forEach(value -> unique(kinds, value.name(),
                value.isOrganizationalContext() ? "OrgCtx" : "Group", errors));
        if (!model.isRevisedCore() && kinds.containsKey("Agent")) {
            errors.add("classifier name 'Agent' is reserved for the generated CSL Agent class");
        }
        return kinds;
    }

    private static void validateGeneralizations(CslModel model, Map<String, String> kinds,
            List<String> errors) {
        Map<String, String> parent = new LinkedHashMap<>();
        for (CslGeneralization generalization : model.generalizations()) {
            String specificKind = kinds.get(generalization.specific());
            String generalKind = kinds.get(generalization.general());
            if (specificKind == null || generalKind == null) {
                errors.add("specialization '" + generalization.specific() + " -> "
                        + generalization.general() + "' references an unknown classifier");
                continue;
            }
            if (!specificKind.equals(generalKind)) {
                errors.add("specialization '" + generalization.specific() + " -> "
                        + generalization.general() + "' must connect classifiers of the same kind");
                continue;
            }
            if (generalization.specific().equals(generalization.general())) {
                errors.add("classifier '" + generalization.specific() + "' cannot specialize itself");
            }
            String previous = parent.putIfAbsent(generalization.specific(), generalization.general());
            if (previous != null && !previous.equals(generalization.general())) {
                errors.add("classifier '" + generalization.specific() + "' has more than one parent: '"
                        + previous + "' and '" + generalization.general() + "'");
            }
        }
        for (String classifier : parent.keySet()) {
            Set<String> path = new LinkedHashSet<>();
            String current = classifier;
            while (current != null) {
                if (!path.add(current)) {
                    errors.add("specialization cycle involving '" + current + "'");
                    break;
                }
                current = parent.get(current);
            }
        }
    }

    private static void validateInheritedProperties(CslModel model, List<String> errors) {
        Map<String, List<CslAttribute>> attributes = new HashMap<>();
        model.entities().forEach(value -> attributes.put(value.name(), value.attributes()));
        model.groups().forEach(value -> attributes.put(value.name(), value.attributes()));
        Map<String, String> parent = new HashMap<>();
        model.entities().forEach(value -> value.specializes().ifPresent(p -> parent.put(value.name(), p)));
        model.groups().forEach(value -> value.specializes().ifPresent(p -> parent.put(value.name(), p)));
        if (model.isRevisedCore()) {
            model.roles().forEach(value -> {
                attributes.put(value.name(), value.attributes());
                value.parentRoles().stream().findFirst().ifPresent(p -> parent.put(value.name(), p));
            });
        }

        for (String classifier : attributes.keySet()) {
            Map<String, String> inherited = new LinkedHashMap<>();
            String current = classifier;
            Set<String> seen = new HashSet<>();
            while (current != null && seen.add(current)) {
                for (CslAttribute attribute : attributes.getOrDefault(current, List.of())) {
                    String previous = inherited.putIfAbsent(attribute.name(), attribute.type().sourceName());
                    if (previous != null) {
                        errors.add("property '" + attribute.name() + "' is redefined in inheritance chain of '"
                                + classifier + "'");
                    }
                }
                current = parent.get(current);
            }
        }
    }

    /** Reference-dependent M2 rules of CSL core v4, mirrored in csl.ecore. */
    private static void validateCore(CslModel model, Map<String, String> kinds, List<String> errors) {
        Set<String> objectNames = new LinkedHashSet<>();
        model.entities().forEach(x -> objectNames.add(x.name()));
        model.roles().forEach(x -> objectNames.add(x.name()));
        model.orgContexts().forEach(x -> objectNames.add(x.name()));

        Map<String, String> declarationOwner = new LinkedHashMap<>();
        Map<String, String> contextParent = new LinkedHashMap<>();
        for (CslGroup context : model.groups()) {
            if (!context.isOrganizationalContext() || context.specializes().isPresent()) {
                errors.add("core v4 has OrgCtx nesting, not Group or OrgCtx generalization");
            }
            Set<String> localNames = new HashSet<>();
            for (CslGroupMember member : context.members()) {
                if (!objectNames.contains(member.type()))
                    errors.add("unknown context member '" + member.type() + "'");
                if (!localNames.add(member.type()))
                    errors.add("duplicate member '" + member.type() + "' in OrgCtx '" + context.name() + "'");
                String previous = declarationOwner.putIfAbsent(member.type(), context.name());
                if (previous != null && !previous.equals(context.name()))
                    errors.add("declaration '" + member.type() + "' is contained by more than one OrgCtx");
                if ("OrgCtx".equals(kinds.get(member.type())))
                    contextParent.put(member.type(), context.name());
            }
        }
        detectCycles(contextParent, "OrgCtx nesting", errors);

        Set<String> relationNames = new HashSet<>();
        for (CslRelation relation : model.relations()) {
            if (!relationNames.add(relation.name())) errors.add("duplicate relationship '" + relation.name() + "'");
            Set<String> endRoles = new HashSet<>();
            int nonEntityEnds = 0;
            for (CslEndpoint end : relation.endpoints()) {
                if (!objectNames.contains(end.type()))
                    errors.add("relationship '" + relation.name() + "' has unknown Object end '" + end.type() + "'");
                if (!"Entity".equals(kinds.get(end.type()))) nonEntityEnds++;
                end.roleName().ifPresent(name -> {
                    if (!endRoles.add(name))
                        errors.add("relationship '" + relation.name() + "' has duplicate end role '" + name + "'");
                });
            }
            if (nonEntityEnds > 1)
                errors.add("EntityCenteredAssociation: '" + relation.name()
                        + "' may have at most one non-Entity end; use role generalization,"
                        + " compatibility or OrgCtx nesting for organizational relationships");
            if (relation.kind() != RelationKind.ASSOCIATION && relation.endpoints().size() != 2)
                errors.add("aggregation/composition '" + relation.name() + "' must have exactly two ends");
            if (relation.kind() == RelationKind.COMPOSITION) {
                CslCardinality whole = relation.endpoints().get(0).multiplicity();
                if (whole.max().isEmpty() || whole.max().getAsInt() != 1)
                    errors.add("composition '" + relation.name() + "' must have whole-end upper bound 1");
            }
        }

        Set<String> pairs = new HashSet<>();
        for (CslCompatibility compatibility : model.compatibilities()) {
            String a = compatibility.fromRole(), b = compatibility.toRole();
            if (!"Role".equals(kinds.get(a)) || !"Role".equals(kinds.get(b)))
                errors.add("compatibility endpoints must be declared Roles: '" + a + "', '" + b + "'");
            if (a.equals(b)) errors.add("Role '" + a + "' cannot declare compatibility with itself");
            String key = a.compareTo(b) < 0 ? a + "\0" + b : b + "\0" + a;
            if (!pairs.add(key)) errors.add("duplicate symmetric compatibility between '" + a + "' and '" + b + "'");
        }
    }

    private static void validateRelations(List<CslRelation> relations, Set<String> entities,
            Set<String> groups, Set<String> classifiers, List<String> errors) {
        Set<String> names = new HashSet<>();
        Set<String> groupCompositionPairs = new HashSet<>();
        for (CslRelation relation : relations) {
            if (!names.add(relation.name())) errors.add("duplicate relationship '" + relation.name() + "'");
            if (!classifiers.contains(relation.source().type()) || !classifiers.contains(relation.target().type())) {
                errors.add("relationship '" + relation.name() + "' references an unknown endpoint classifier");
                continue;
            }
            if (relation.source().roleName().isPresent() && relation.target().roleName().isPresent()
                    && relation.source().roleName().get().equals(relation.target().roleName().get())) {
                errors.add("relationship '" + relation.name() + "' has duplicate MemberEnd role name '"
                        + relation.source().roleName().get() + "'");
            }
            boolean sourceEntity = entities.contains(relation.source().type());
            boolean targetIsGroupOrRole = groups.contains(relation.target().type())
                    || !entities.contains(relation.target().type());
            if (relation.kind() == RelationKind.COMPOSITION && sourceEntity
                    && targetIsGroupOrRole) {
                errors.add("relationship '" + relation.name()
                        + "': a Role or Group cannot be owned by an Entity");
            }
            if (relation.kind() == RelationKind.COMPOSITION
                    && groups.contains(relation.source().type())
                    && targetIsGroupOrRole) {
                if (relation.source().type().equals(relation.target().type())) {
                    errors.add("relationship '" + relation.name()
                            + "' cannot compose a Group into itself");
                } else if (!groupCompositionPairs.add(relation.source().type() + "\0"
                        + relation.target().type())) {
                    errors.add("Group '" + relation.source().type() + "' and member '"
                            + relation.target().type() + "' have more than one composition");
                }
            }
        }
    }

    private record OwnerIndex(Map<String, String> targetOwner, Map<String, String> groupParent) {}

    private static OwnerIndex validateOwners(List<CslOwner> owners, Set<String> roles, Set<String> groups,
            List<String> errors) {
        Map<String, String> targetOwner = new LinkedHashMap<>();
        Map<String, String> groupParent = new LinkedHashMap<>();
        for (CslOwner owner : owners) {
            if (!groups.contains(owner.sourceGroup())) {
                errors.add("Owner source '" + owner.sourceGroup() + "' must be a Group");
            }
            boolean roleTarget = roles.contains(owner.target());
            boolean groupTarget = groups.contains(owner.target());
            if (!roleTarget && !groupTarget) {
                errors.add("Owner target '" + owner.target()
                        + "' must be a Role or Group; Entity members are forbidden");
                continue;
            }
            String previous = targetOwner.putIfAbsent(owner.target(), owner.sourceGroup());
            if (previous != null) {
                errors.add("Owner target '" + owner.target() + "' has more than one owner Group: '"
                        + previous + "' and '" + owner.sourceGroup() + "'");
            }
            if (groupTarget) groupParent.put(owner.target(), owner.sourceGroup());
        }
        detectCycles(groupParent, "Group Owner", errors);
        return new OwnerIndex(Map.copyOf(targetOwner), Map.copyOf(groupParent));
    }

    /** A child Role's owned scope may stay equal or narrow down the Group Owner tree. */
    private static void validateRoleOwnerScopeMonotonicity(Map<String, CslRole> roles,
            OwnerIndex owners, List<String> errors) {
        for (CslRole child : roles.values()) {
            String childOwner = owners.targetOwner().get(child.name());
            String ancestorName = child.name();
            Set<String> seen = new LinkedHashSet<>();
            while (seen.add(ancestorName)) {
                CslRole current = roles.get(ancestorName);
                if (current == null || current.parentRoles().isEmpty()) break;
                ancestorName = current.parentRoles().get(0);
                String ancestorOwner = owners.targetOwner().get(ancestorName);
                if (ancestorOwner == null) continue;
                if (childOwner == null) {
                    errors.add("Role '" + child.name() + "' specializes owned Role '" + ancestorName
                            + "' in Group '" + ancestorOwner + "' but has no Owner; a child Role scope"
                            + " cannot be wider than its parent Role scope");
                } else if (!sameOrDescendantGroup(childOwner, ancestorOwner, owners.groupParent())) {
                    errors.add("invalid Role parent: Role '" + child.name() + "' is owned by Group '"
                            + childOwner + "' and specializes Role '" + ancestorName + "' owned by unrelated"
                            + " Group '" + ancestorOwner + "'; the parent Role Group must be the same Group"
                            + " or an Owner ancestor of the child Role Group");
                }
            }
        }
    }

    private static void validateCompatibility(List<CslCompatibility> compatibilities,
            Map<String, CslRole> roles, Set<String> groups, OwnerIndex owners,
            List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (CslCompatibility compatibility : compatibilities) {
            if (!roles.containsKey(compatibility.fromRole()) || !roles.containsKey(compatibility.toRole())) {
                errors.add("Compatibility endpoints must both be declared Roles: '"
                        + compatibility.fromRole() + "' and '" + compatibility.toRole() + "'");
                continue;
            }
            if (compatibility.fromRole().equals(compatibility.toRole())) {
                errors.add("Compatibility endpoints must be two different Role types; self-compatibility for '"
                        + compatibility.fromRole() + "' cannot override NoSelfConflict");
            }
            if (!groups.contains(compatibility.groupName())) {
                errors.add("Compatibility between '" + compatibility.fromRole() + "' and '"
                        + compatibility.toRole() + "' must be declared inside an existing Group");
                continue;
            }
            validateCompatibilityEndpointScope(compatibility.fromRole(),
                    compatibility.groupName(), owners, errors);
            validateCompatibilityEndpointScope(compatibility.toRole(),
                    compatibility.groupName(), owners, errors);
            String a = compatibility.fromRole().compareTo(compatibility.toRole()) <= 0
                    ? compatibility.fromRole() : compatibility.toRole();
            String b = a.equals(compatibility.fromRole())
                    ? compatibility.toRole() : compatibility.fromRole();
            if (!seen.add(compatibility.groupName() + "\0" + a + "\0" + b)) {
                errors.add("duplicate Compatibility between '" + a + "' and '" + b
                        + "' in Group '" + compatibility.groupName() + "'");
            }
        }
    }

    private static void validateCompatibilityEndpointScope(String role, String declaringGroup,
            OwnerIndex owners, List<String> errors) {
        String owner = owners.targetOwner().get(role);
        if (owner != null && !sameOrDescendantGroup(owner, declaringGroup, owners.groupParent())) {
            errors.add("Compatibility Role '" + role + "' is never owned by declaring Group '"
                    + declaringGroup + "' or one of its nested Groups; its actual owner is '" + owner + "'");
        }
    }

    private static boolean sameOrDescendantGroup(String candidate, String ancestor,
            Map<String, String> groupParent) {
        String current = candidate;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current.equals(ancestor)) return true;
            current = groupParent.get(current);
        }
        return false;
    }

    private static void detectCycles(Map<String, String> parent, String label, List<String> errors) {
        for (String node : parent.keySet()) {
            Set<String> path = new LinkedHashSet<>();
            String current = node;
            while (current != null) {
                if (!path.add(current)) {
                    errors.add(label + " cycle involving '" + current + "'");
                    break;
                }
                current = parent.get(current);
            }
        }
    }

    private static Set<String> names(List<String> values) {
        return new LinkedHashSet<>(values);
    }

    private static void unique(Map<String, String> kinds, String name, String kind, List<String> errors) {
        String previous = kinds.putIfAbsent(name, kind);
        if (previous != null) {
            errors.add("duplicate classifier name '" + name + "' (already declared as " + previous + ")");
        }
    }
}
