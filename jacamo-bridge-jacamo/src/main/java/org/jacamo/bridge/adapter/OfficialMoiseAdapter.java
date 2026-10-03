package org.jacamo.bridge.adapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import moise.os.Cardinality;
import moise.os.OS;
import moise.os.fs.FS;
import moise.os.fs.Goal;
import moise.os.fs.Mission;
import moise.os.fs.Plan;
import moise.os.fs.Scheme;
import moise.os.ns.NS;
import moise.os.ns.Norm;
import moise.os.ss.Compatibility;
import moise.os.ss.Group;
import moise.os.ss.Link;
import moise.os.ss.Role;
import moise.os.ss.RoleRel;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.BridgeRelationId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ModelFact;
import org.jacamo.bridge.contract.RelationCardinality;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.CompatibilitySemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.FunctionalSpecificationSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.GroupRoleCardinalitySemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.GroupSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.LinkSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.MissionSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.NormSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.NormativeSpecificationSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.OrganizationalGoalSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.OrganizationalPlanSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.OrganizationSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.RoleRelationSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.RoleSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.SchemeMissionCardinalitySemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.SchemeSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.StructuralSpecificationSemantic;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.SubGroupCardinalitySemantic;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;

/** Official Moise object-graph adapter; normative policies are not Boolean OCL invariants. */
public final class OfficialMoiseAdapter {
    public record Result(List<ModelFact> facts, List<RelationCardinality> groupRoleCardinalities,
                         List<RelationCardinality> parentSubGroupCardinalities,
                         OrganizationSemantic organization) {
        public Result {
            facts = List.copyOf(facts);
            groupRoleCardinalities = List.copyOf(groupRoleCardinalities);
            parentSubGroupCardinalities = List.copyOf(parentSubGroupCardinalities);
        }
    }

    /** Loads one OS through the official loader and copies the supported static object graph. */
    public Result load(Path projectRoot, Path osFile, String projectKey) throws Exception {
        Path exact = osFile.toAbsolutePath().normalize();
        OS os = OS.loadOSFromURI(exact.toUri().toString());
        if (os == null) throw new IllegalArgumentException("MOISE_OFFICIAL_OS_LOAD_FAILED:" + exact);
        var evidence = AdapterEvidence.file("moise-os", projectRoot, exact,
                "OS.loadOSFromURI official graph");
        String osId = required(os.getId(), "MOISE_OS_ID_UNAVAILABLE");
        String organizationId = "moise:organization:" + projectKey + ":" + osId;

        var facts = new ArrayList<ModelFact>();
        var roleCards = new ArrayList<RelationCardinality>();
        var subgroupCards = new ArrayList<RelationCardinality>();
        var organization = copyOrganization(os, organizationId, exact.toString(), projectKey, evidence,
                facts, roleCards, subgroupCards);
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        roleCards.sort(Comparator.comparing(card -> card.id().canonical()));
        subgroupCards.sort(Comparator.comparing(card -> card.id().canonical()));
        return new Result(facts, roleCards, subgroupCards, organization);
    }

    private OrganizationSemantic copyOrganization(OS os, String organizationId, String sourceUri,
                                                   String projectKey, org.jacamo.bridge.contract.Evidence evidence,
                                                   List<ModelFact> facts,
                                                   List<RelationCardinality> roleCards,
                                                   List<RelationCardinality> subgroupCards) {
        var rolesByObject = new IdentityHashMap<Role, String>();
        var groupsByObject = new IdentityHashMap<Group, String>();
        var missionsByObject = new IdentityHashMap<Mission, String>();
        var goalsByObject = new IdentityHashMap<Goal, String>();
        var plansByObject = new IdentityHashMap<Plan, String>();
        String structuralId = organizationId + ":structural-specification";
        String functionalId = organizationId + ":functional-specification";
        String normativeId = organizationId + ":normative-specification";

        for (Role role : sorted(os.getSS().getRolesDef(), Comparator.comparing(Role::getId)))
            rolesByObject.put(role, roleId(organizationId, role));
        var groups = new ArrayList<Group>();
        collectGroups(os.getSS().getRootGrSpec(), groups);
        for (Group group : groups) groupsByObject.put(group, groupId(organizationId, group));

        var roleDtos = new ArrayList<RoleSemantic>();
        for (Role role : sorted(os.getSS().getRolesDef(), Comparator.comparing(Role::getId))) {
            String id = rolesByObject.get(role);
            roleDtos.add(new RoleSemantic(metadata(id, "MOISE_ROLE", Role.class.getName(), evidence), role.getId(),
                    role.isAbstract(), role.getSuperRoles().stream().sorted(Comparator.comparing(Role::getId))
                            .map(rolesByObject::get).toList()));
            facts.add(new ModelFact("role", id(projectKey, "role", id),
                    Map.of("roleId", role.getId(), "abstract", Boolean.toString(role.isAbstract())),
                    Map.of("superRoles", role.getSuperRoles().stream().sorted(Comparator.comparing(Role::getId))
                            .map(value -> id(projectKey, "role", rolesByObject.get(value))).toList()),
                    CapabilityStatus.COMPLETE, List.of(evidence)));
        }

        var groupDtos = new ArrayList<GroupSemantic>();
        var roleRelationDtos = new ArrayList<RoleRelationSemantic>();
        var linkDtos = new ArrayList<LinkSemantic>();
        var compatibilityDtos = new ArrayList<CompatibilitySemantic>();
        var groupRoleCardinalityDtos = new ArrayList<GroupRoleCardinalitySemantic>();
        var subgroupCardinalityDtos = new ArrayList<SubGroupCardinalitySemantic>();
        for (Group group : groups) {
            String groupSemanticId = groupsByObject.get(group);
            List<String> groupRoles = sorted(group.getRoles().getAll(), Comparator.comparing(Role::getId)).stream()
                    .map(rolesByObject::get).toList();
            List<Group> children = sorted(group.getSubGroups().getAll(), Comparator.comparing(OfficialMoiseAdapter::groupSortKey));
            List<String> childIds = children.stream().map(groupsByObject::get).toList();
            String parentId = groupsByObject.get(group.getSuperGroup());
            if (parentId == null) parentId = "";
            groupDtos.add(new GroupSemantic(metadata(groupSemanticId, "MOISE_GROUP", Group.class.getName(), evidence),
                    group.getId(), parentId, groupRoles, childIds));
            facts.add(new ModelFact("group", id(projectKey, "group", groupSemanticId), Map.of("groupId", group.getId()),
                    parentId.isBlank() ? Map.of() : Map.of("parent", List.of(id(projectKey, "group", parentId))),
                    CapabilityStatus.COMPLETE, List.of(evidence)));

            for (Role role : sorted(group.getRoles().getAll(), Comparator.comparing(Role::getId))) {
                Cardinality cardinality = group.getRoleCardinality(role);
                String relationId = groupSemanticId + ":role-cardinality:" + rolesByObject.get(role);
                groupRoleCardinalityDtos.add(new GroupRoleCardinalitySemantic(
                        metadata(relationId, "MOISE_GROUP_ROLE_CARDINALITY", Cardinality.class.getName(), evidence),
                        groupSemanticId, rolesByObject.get(role), cardinality.getMin(), cardinality.getMax()));
                var relation = new BridgeRelationId("group-role-cardinality",
                        List.of(id(projectKey, "group", groupSemanticId), id(projectKey, "role", rolesByObject.get(role))),
                        evidence.evidenceId(), "model");
                roleCards.add(new RelationCardinality(relation, relation.endpoints().get(0), relation.endpoints().get(1),
                        cardinality.getMin(), cardinality.getMax(), List.of(evidence)));
            }
            for (Group child : children) {
                Cardinality cardinality = group.getSubGroupCardinality(child);
                String relationId = groupSemanticId + ":subgroup-cardinality:" + groupsByObject.get(child);
                subgroupCardinalityDtos.add(new SubGroupCardinalitySemantic(
                        metadata(relationId, "MOISE_SUBGROUP_CARDINALITY", Cardinality.class.getName(), evidence),
                        groupSemanticId, groupsByObject.get(child), cardinality.getMin(), cardinality.getMax()));
                var relation = new BridgeRelationId("parent-subgroup-cardinality",
                        List.of(id(projectKey, "group", groupSemanticId), id(projectKey, "group", groupsByObject.get(child))),
                        evidence.evidenceId(), "model");
                subgroupCards.add(new RelationCardinality(relation, relation.endpoints().get(0), relation.endpoints().get(1),
                        cardinality.getMin(), cardinality.getMax(), List.of(evidence)));
            }

            for (Link link : sorted(group.getLinks(), Comparator.comparing(value -> relationSortKey(value, "link")))) {
                String relationId = relationId(groupSemanticId, link, "link", link.getTypeStr());
                roleRelationDtos.add(roleRelation(link, relationId, "LINK", groupSemanticId, rolesByObject, evidence));
                linkDtos.add(new LinkSemantic(metadata(relationId + ":link", "MOISE_LINK", Link.class.getName(), evidence),
                        relationId, required(link.getTypeStr(), "MOISE_LINK_TYPE_UNAVAILABLE")));
            }
            for (Compatibility compatibility : sorted(group.getCompatibilities(),
                    Comparator.comparing(value -> relationSortKey(value, "compatibility")))) {
                String relationId = relationId(groupSemanticId, compatibility, "compatibility", "");
                roleRelationDtos.add(roleRelation(compatibility, relationId, "COMPATIBILITY", groupSemanticId,
                        rolesByObject, evidence));
                compatibilityDtos.add(new CompatibilitySemantic(
                        metadata(relationId + ":compatibility", "MOISE_COMPATIBILITY", Compatibility.class.getName(), evidence),
                        relationId));
            }
        }

        var schemes = new ArrayList<SchemeSemantic>();
        var schemeMissionCardinalityDtos = new ArrayList<SchemeMissionCardinalitySemantic>();
        FS fs = os.getFS();
        for (Scheme scheme : sorted(fs.getSchemes(), Comparator.comparing(Scheme::getId))) {
            String schemeSemanticId = schemeId(organizationId, scheme);
            List<Mission> missions = sorted(scheme.getMissions(), Comparator.comparing(Mission::getId));
            for (Mission mission : missions) missionsByObject.put(mission, missionId(schemeSemanticId, mission));
            List<Goal> goals = sorted(scheme.getGoals(), Comparator.comparing(Goal::getId));
            for (Goal goal : goals) goalsByObject.put(goal, goalId(schemeSemanticId, goal));
            List<Plan> plans = sorted(scheme.getPlans(), Comparator.comparing(OfficialMoiseAdapter::planSortKey));
            for (int planIndex = 0; planIndex < plans.size(); planIndex++)
                plansByObject.put(plans.get(planIndex), planId(schemeSemanticId, plans.get(planIndex), planIndex));

            var missionDtos = new ArrayList<MissionSemantic>();
            for (Mission mission : missions) {
                String missionSemanticId = missionsByObject.get(mission);
                missionDtos.add(new MissionSemantic(metadata(missionSemanticId, "MOISE_MISSION",
                                Mission.class.getName(), evidence), mission.getId(), schemeSemanticId,
                        sorted(mission.getGoals(), Comparator.comparing(Goal::getId)).stream()
                                .map(goalsByObject::get).toList()));
                Cardinality cardinality = scheme.getMissionCardinality(mission);
                String cardinalityId = schemeSemanticId + ":mission-cardinality:" + missionSemanticId;
                schemeMissionCardinalityDtos.add(new SchemeMissionCardinalitySemantic(
                        metadata(cardinalityId, "MOISE_SCHEME_MISSION_CARDINALITY", Cardinality.class.getName(), evidence),
                        schemeSemanticId, missionSemanticId, cardinality.getMin(), cardinality.getMax()));
            }

            var planDtos = new ArrayList<OrganizationalPlanSemantic>();
            for (Plan plan : plans) {
                String planSemanticId = plansByObject.get(plan);
                planDtos.add(new OrganizationalPlanSemantic(metadata(planSemanticId, "MOISE_OPLAN",
                                Plan.class.getName(), evidence), planSemanticId, schemeSemanticId,
                        goalsByObject.get(plan.getTargetGoal()), enumName(plan.getOp()), plan.getSuccessRate(),
                        plan.getSubGoals().stream().map(goalsByObject::get).toList()));
            }

            var goalDtos = new ArrayList<OrganizationalGoalSemantic>();
            for (Goal goal : goals) {
                String goalSemanticId = goalsByObject.get(goal);
                goalDtos.add(new OrganizationalGoalSemantic(metadata(goalSemanticId, "MOISE_GOAL",
                        Goal.class.getName(), evidence), goal.getId(), schemeSemanticId, enumName(goal.getType()),
                        text(goal.getDescription()), canonical(goal.getArguments()), goal.getMinAgToSatisfy(),
                        text(goal.getTTF()), text(goal.getLocation()), (goal.getDependencies() == null ? List.<Goal>of()
                                : goal.getDependencies()).stream()
                                .map(goalsByObject::get).toList(), plansByObject.get(goal.getPlan()),
                        planSemanticId(goal.getInPlan(), plansByObject)));
            }
            String rootGoalId = scheme.getRoot() == null ? "" : goalsByObject.get(scheme.getRoot());
            schemes.add(new SchemeSemantic(metadata(schemeSemanticId, "MOISE_SCHEME", Scheme.class.getName(), evidence),
                    scheme.getId(), functionalId, rootGoalId, missionDtos, goalDtos, planDtos));

            var schemeReferences = new java.util.LinkedHashMap<String, List<BridgeEntityId>>();
            if (!rootGoalId.isBlank()) schemeReferences.put("rootGoal", List.of(id(projectKey,
                    "organisational-goal", rootGoalId)));
            schemeReferences.put("missions", missions.stream().map(mission -> id(projectKey, "mission",
                    missionsByObject.get(mission))).toList());
            facts.add(new ModelFact("scheme", id(projectKey, "scheme", schemeSemanticId), Map.of("schemeId", scheme.getId()),
                    schemeReferences, CapabilityStatus.COMPLETE, List.of(evidence)));
            for (Mission mission : missions) facts.add(new ModelFact("mission",
                    id(projectKey, "mission", missionsByObject.get(mission)), Map.of("missionId", mission.getId()), Map.of("scheme", List.of(id(projectKey,
                            "scheme", schemeSemanticId)), "goals", mission.getGoals().stream()
                            .sorted(Comparator.comparing(Goal::getId)).map(goal -> id(projectKey, "organisational-goal",
                                    goalsByObject.get(goal))).toList()), CapabilityStatus.COMPLETE, List.of(evidence)));
            for (Goal goal : goals) {
                var goalReferences = new java.util.LinkedHashMap<String, List<BridgeEntityId>>();
                goalReferences.put("scheme", List.of(id(projectKey, "scheme", schemeSemanticId)));
                if (goal.getPlan() != null) goalReferences.put("plan", List.of(id(projectKey,
                        "organisational-plan", plansByObject.get(goal.getPlan()))));
                facts.add(new ModelFact("organisational-goal",
                        id(projectKey, "organisational-goal", goalsByObject.get(goal)), Map.of("goalId", goal.getId(), "description", text(goal.getDescription()),
                                "type", enumName(goal.getType()), "minAgents", Integer.toString(goal.getMinAgToSatisfy()),
                                "ttf", text(goal.getTTF())), goalReferences,
                        CapabilityStatus.COMPLETE, List.of(evidence)));
            }
            for (Plan plan : plans) facts.add(new ModelFact("organisational-plan", id(projectKey, "organisational-plan",
                    plansByObject.get(plan)), Map.of("operator", enumName(plan.getOp()), "ast", plan.toString()),
                    Map.of("scheme", List.of(id(projectKey, "scheme", schemeSemanticId)), "targetGoal", List.of(id(projectKey,
                            "organisational-goal", goalsByObject.get(plan.getTargetGoal()))), "subGoals", plan.getSubGoals().stream()
                            .map(goal -> id(projectKey, "organisational-goal", goalsByObject.get(goal))).toList()),
                    CapabilityStatus.COMPLETE, List.of(evidence)));
        }

        var norms = new ArrayList<NormSemantic>();
        NS ns = os.getNS();
        for (Norm norm : sorted(ns.getNorms(), Comparator.comparing(Norm::getId))) {
            String normSemanticId = organizationId + ":norm:" + norm.getId();
            String roleSemanticId = rolesByObject.get(norm.getRole());
            String missionSemanticId = missionsByObject.get(norm.getMission());
            norms.add(new NormSemantic(metadata(normSemanticId, "MOISE_NORM", Norm.class.getName(), evidence),
                    norm.getId(), normativeId, roleSemanticId, missionSemanticId, enumName(norm.getType()),
                    text(norm.getCondition()), norm.getTimeConstraint() == null ? "" : text(norm.getTimeConstraint().getTC())));
            var normReferences = new java.util.LinkedHashMap<String, List<BridgeEntityId>>();
            if (norm.getRole() != null) normReferences.put("role", List.of(id(projectKey, "role",
                    roleSemanticId)));
            if (norm.getMission() != null) normReferences.put("mission", List.of(id(projectKey, "mission",
                    missionSemanticId)));
            facts.add(new ModelFact("norm", id(projectKey, "norm", normSemanticId),
                    Map.of("normId", norm.getId(), "type", enumName(norm.getType()), "condition", text(norm.getCondition()), "timeConstraint",
                            norm.getTimeConstraint() == null ? "" : text(norm.getTimeConstraint().getTC())),
                    normReferences, CapabilityStatus.COMPLETE, List.of(evidence)));
        }

        var structural = new StructuralSpecificationSemantic(
                metadata(structuralId, "MOISE_SS_CONTAINER", "moise.os.ss.SS", evidence), organizationId,
                structuralId, groupsByObject.getOrDefault(os.getSS().getRootGrSpec(), ""), roleDtos,
                groupDtos, roleRelationDtos, linkDtos, compatibilityDtos, groupRoleCardinalityDtos,
                subgroupCardinalityDtos);
        var functional = new FunctionalSpecificationSemantic(
                metadata(functionalId, "MOISE_FS_CONTAINER", FS.class.getName(), evidence), organizationId,
                functionalId, schemes, schemeMissionCardinalityDtos);
        var normative = new NormativeSpecificationSemantic(
                metadata(normativeId, "MOISE_NS_CONTAINER", NS.class.getName(), evidence), organizationId,
                normativeId, norms);
        return new OrganizationSemantic(metadata(organizationId, "MOISE_OS", OS.class.getName(), evidence),
                os.getId(), sourceUri, structural, functional, normative);
    }

    private RoleRelationSemantic roleRelation(RoleRel relation, String relationId, String kind, String groupId,
                                               IdentityHashMap<Role, String> roles,
                                               org.jacamo.bridge.contract.Evidence evidence) {
        return new RoleRelationSemantic(metadata(relationId, "MOISE_ROLE_RELATION", RoleRel.class.getName(), evidence),
                relationId, kind, groupId, relation.getSource() == null ? "" : roles.get(relation.getSource()),
                relation.getTarget() == null ? "" : roles.get(relation.getTarget()),
                relation.getScope() == null ? "" : relation.getScope().toString(),
                relation.getExtendsToSubGroups(), relation.isBiDir());
    }

    private static void collectGroups(Group group, List<Group> result) {
        if (group == null) return;
        result.add(group);
        for (Group child : sorted(group.getSubGroups().getAll(), Comparator.comparing(OfficialMoiseAdapter::groupSortKey)))
            collectGroups(child, result);
    }

    private static String relationId(String groupId, RoleRel relation, String kind, String extra) {
        String key = groupId + "|" + kind + "|" + roleKey(relation.getSource()) + "|" + roleKey(relation.getTarget())
                + "|" + enumName(relation.getScope()) + "|" + relation.getExtendsToSubGroups() + "|"
                + relation.isBiDir() + "|" + extra;
        return groupId + ":role-relation:" + AdapterEvidence.digest(key.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    }

    private static String relationSortKey(RoleRel relation, String kind) {
        return kind + "|" + roleKey(relation.getSource()) + "|" + roleKey(relation.getTarget()) + "|"
                + enumName(relation.getScope()) + "|" + relation.getExtendsToSubGroups() + "|" + relation.isBiDir();
    }

    private static String planSortKey(Plan plan) {
        return stablePlanKey(plan);
    }

    private static String stablePlanKey(Plan plan) {
        return text(plan.getTargetGoal() == null ? "" : plan.getTargetGoal().getFullId()) + "|"
                + enumName(plan.getOp()) + "|"
                + plan.getSubGoals().stream().map(Goal::getFullId).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static String groupSortKey(Group group) { return text(group.getFullId()) + "|" + text(group.getId()); }
    private static String roleKey(Role role) { return role == null ? "" : text(role.getFullId()) + "|" + text(role.getId()); }

    private static String roleId(String organizationId, Role role) { return organizationId + ":role:" + text(role.getId()); }
    private static String groupId(String organizationId, Group group) { return organizationId + ":group:" + groupSortKey(group); }
    private static String schemeId(String organizationId, Scheme scheme) { return organizationId + ":scheme:" + scheme.getId(); }
    private static String missionId(String schemeId, Mission mission) { return schemeId + ":mission:" + mission.getId(); }
    private static String goalId(String schemeId, Goal goal) { return schemeId + ":goal:" + goal.getId(); }

    private static String planId(String schemeId, Plan plan, int ordinal) {
        String exact = AdapterEvidence.digest(stablePlanKey(plan).getBytes(StandardCharsets.UTF_8)).substring(0, 24);
        return schemeId + ":plan:" + exact + ":" + ordinal;
    }

    private static String planSemanticId(Plan plan, IdentityHashMap<Plan, String> plans) {
        return plan == null ? "" : text(plans.get(plan));
    }

    private static String canonical(Object value) {
        return new String(CanonicalJson.encode(value == null ? Map.of() : value), StandardCharsets.UTF_8);
    }

    private static SemanticMetadata metadata(String id, String kind, String fqcn,
                                             org.jacamo.bridge.contract.Evidence evidence) {
        return SemanticEvidence.metadata(id, kind, fqcn, EvidenceAuthority.OFFICIAL_MOISE_API,
                Fidelity.EXACT, CapabilityStatus.COMPLETE, evidence, 1, 1, List.of());
    }

    private static String required(String value, String diagnostic) {
        if (value == null || value.isBlank()) throw new IllegalStateException(diagnostic);
        return value;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }

    private static String enumName(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.name() : text(value);
    }

    private BridgeEntityId id(String projectKey, String kind, String local) {
        return new BridgeEntityId("moise", "organisation", kind, projectKey, local, "model");
    }

    private static <T> List<T> sorted(java.util.Collection<T> values, Comparator<T> comparator) {
        return values == null ? List.of() : values.stream().sorted(comparator).toList();
    }
}
