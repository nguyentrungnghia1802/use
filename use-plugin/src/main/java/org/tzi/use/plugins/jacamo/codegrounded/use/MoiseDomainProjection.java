package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.OrganizationSemantic;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ConstraintMigrationStatus;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeConstraintSpec;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.uml.mm.MAggregationKind;
import org.tzi.use.uml.mm.MElementAnnotation;
import org.tzi.use.uml.mm.MModelElement;
import org.tzi.use.uml.ocl.expr.ExpConstEnum;
import org.tzi.use.uml.ocl.expr.ExpConstInteger;
import org.tzi.use.uml.ocl.expr.ExpConstReal;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.Expression;

/**
 * Specializes the supported official OS graph into an organisation-specific enactment schema.
 * Static definitions become types/policies, NOT fake OE/RolePlayer/MissionPlayer instances.
 * The four per-OS abstract supports give role adoption and mission commitment their own identity;
 * they do not turn a role into an Agent or a mission into a Jason goal.
 */
public final class MoiseDomainProjection {
    public static final String ANNOTATION = "MoiseProjection";
    public static final Map<String, String> INSPECTION_RULES = Map.ofEntries(
            Map.entry("Organization", "M01"), Map.entry("StructuralSpecification", "M02"),
            Map.entry("FunctionalSpecification", "M03"), Map.entry("NormativeSpecification", "M04"),
            Map.entry("Group", "M05"), Map.entry("Role", "M06"), Map.entry("RoleRelation", "M07"),
            Map.entry("Link", "M08"), Map.entry("Compatibility", "M09"), Map.entry("Scheme", "M10"),
            Map.entry("Mission", "M11"), Map.entry("OrganizationalGoal", "M12"),
            Map.entry("OrganizationalPlan", "M13"), Map.entry("Norm", "M14"),
            Map.entry("GroupRoleCardinality", "M15"), Map.entry("SubGroupCardinality", "M16"),
            Map.entry("SchemeMissionCardinality", "M17"));

    public record Result(Map<String, String> classNames, List<NativeConstraintSpec> constraints) {
        public Result {
            classNames = Collections.unmodifiableMap(new LinkedHashMap<>(classNames));
            constraints = List.copyOf(constraints);
        }
    }

    private final UseModelApi api;
    private final CodeGroundedTraceCollector trace;
    private final CodeGroundedRuleCatalog catalog = new CodeGroundedRuleCatalog();
    private final Map<String, SemanticMetadata> declarations = new LinkedHashMap<>();
    private final List<NativeConstraintSpec> constraints = new ArrayList<>();
    private Map<String, String> symbols;

    private MoiseDomainProjection(UseModelApi api, CodeGroundedTraceCollector trace) {
        this.api = api; this.trace = trace;
    }

    public static Result install(UseModelApi api, List<OrganizationSemantic> organizations,
                                 CodeGroundedTraceCollector trace) throws UseApiException {
        return new MoiseDomainProjection(api, trace).install(organizations);
    }

    private Result install(List<OrganizationSemantic> organizations) throws UseApiException {
        List<MoiseUseSymbols.Candidate> candidates = new ArrayList<>();
        for (var org : organizations) {
            validate(org);
            candidate(candidates, org.metadata(), "OS", org.name());
            var ss = org.structuralSpecification();
            ss.roles().forEach(role -> candidate(candidates, role.metadata(), "Role", role.roleId()));
            ss.groups().forEach(group -> candidate(candidates, group.metadata(), "Group", group.groupId()));
            for (var scheme : org.functionalSpecification().schemes()) {
                candidate(candidates, scheme.metadata(), "Scheme", scheme.schemeId());
                scheme.missions().forEach(mission -> candidate(candidates, mission.metadata(), "Mission",
                        scheme.schemeId() + "_" + mission.missionId()));
                scheme.goals().forEach(goal -> candidate(candidates, goal.metadata(), "Goal",
                        scheme.schemeId() + "_" + goal.goalId()));
            }
            if (!ss.groups().isEmpty()) support(candidates, org, "GroupInstance");
            if (!ss.roles().isEmpty()) support(candidates, org, "RoleEnactment");
            if (!org.functionalSpecification().schemes().isEmpty()) support(candidates, org, "SchemeInstance");
            if (org.functionalSpecification().schemes().stream().anyMatch(s -> !s.missions().isEmpty()))
                support(candidates, org, "MissionCommitment");
        }
        Set<String> reserved = new TreeSet<>(INSPECTION_RULES.keySet());
        api.getModel().classes().forEach(cls -> reserved.add(cls.name()));
        api.getModel().enumTypes().forEach(type -> reserved.add(type.name()));
        symbols = MoiseUseSymbols.allocate(candidates, reserved);
        for (var org : organizations.stream().sorted(Comparator.comparing(o -> o.metadata().semanticId())).toList())
            organization(org);
        for (var entry : declarations.entrySet()) {
            var cls = api.getModel().getClass(entry.getKey());
            String rule = cls.getAnnotationValue(ANNOTATION, "ruleId");
            trace.add(catalog.require(rule), TracePhase.MODEL_DECLARATION, entry.getValue(), "MClass",
                    "class:" + cls.name(), List.of(cls.getAnnotationValue(ANNOTATION, "representation"),
                            "NO_STATIC_ENACTMENT_OBJECT", "RUNTIME_MATERIALIZATION_EVIDENCE_ONLY"));
            cls.attributes().forEach(attribute -> trace.add(catalog.require(rule), TracePhase.MODEL_DECLARATION,
                    entry.getValue(), "MAttribute", "attribute:" + cls.name() + "." + attribute.name(), List.of()));
        }
        Map<String, String> sourceClasses = new LinkedHashMap<>();
        symbols.forEach((id, name) -> {
            if (declarations.get(name).semanticId().equals(id)) sourceClasses.put(id, name);
        });
        return new Result(sourceClasses, constraints);
    }

    private void organization(OrganizationSemantic org) throws UseApiException {
        String os = name(org.metadata().semanticId());
        createClass(os, org.metadata(), "M01", false, "DOMAIN_SCHEMA");
        api.createAttribute(os, "semanticId", "String");
        String groupBase = supportName(org, "GroupInstance"), roleBase = supportName(org, "RoleEnactment");
        String schemeBase = supportName(org, "SchemeInstance"), missionBase = supportName(org, "MissionCommitment");
        var ss = org.structuralSpecification(); var fs = org.functionalSpecification(); var ns = org.normativeSpecification();
        container(ss.metadata(), "M02", os); container(fs.metadata(), "M03", os); container(ns.metadata(), "M04", os);
        record("M18", org.metadata(), "ContainerPolicyEndpoint", policyIdentity(os, "M02", ss.metadata()), "EXACT_OS_SS_OWNERSHIP");
        record("M19", org.metadata(), "ContainerPolicyEndpoint", policyIdentity(os, "M03", fs.metadata()), "EXACT_OS_FS_OWNERSHIP");
        record("M20", org.metadata(), "ContainerPolicyEndpoint", policyIdentity(os, "M04", ns.metadata()), "EXACT_OS_NS_OWNERSHIP");
        if (groupBase != null) {
            base(groupBase, org.metadata(), "M05", true);
            association("M22", ss.metadata(), os, "organization", "1", groupBase, "groups", "*", false);
        }
        if (roleBase != null) {
            base(roleBase, org.metadata(), "M06", false);
            association("X04", org.metadata(), "Agent", "agent", "1", roleBase,
                    "roleEnactments_" + MoiseUseSymbols.hash(org.metadata().semanticId()), "*", false);
            if (groupBase != null) association("M29", ss.metadata(), groupBase, "group", "1", roleBase, "players", "*", true);
        }
        if (schemeBase != null) {
            base(schemeBase, org.metadata(), "M10", true);
            association("M33", fs.metadata(), os, "organization", "1", schemeBase, "schemes", "*", true);
        }
        if (missionBase != null) {
            base(missionBase, org.metadata(), "M11", false);
            association("M34", fs.metadata(), schemeBase, "scheme", "1", missionBase, "commitments", "*", true);
            association("M11", fs.metadata(), "Agent", "agent", "1", missionBase,
                    "missionCommitments_" + MoiseUseSymbols.hash(org.metadata().semanticId()), "*", false);
        }
        for (var role : ss.roles()) createClass(name(role.metadata().semanticId()), role.metadata(), "M06",
                role.abstractRole(), "DOMAIN_SCHEMA");
        for (var role : ss.roles()) {
            String cls = name(role.metadata().semanticId());
            record("M21", role.metadata(), "TypeMembershipEndpoint", "class:" + cls,
                    "STRUCTURAL_SPECIFICATION=" + ss.metadata().semanticId());
            if (role.superRoleSemanticIds().isEmpty()) api.createGeneralization(cls, roleBase);
            for (String parent : role.superRoleSemanticIds()) {
                api.createGeneralization(cls, name(parent));
                record("M24", role.metadata(), "MGeneralization", "generalization:" + cls + "<" + name(parent),
                        "ROLE_ENTAILMENT_TYPES_NOT_AGENT_INHERITANCE");
            }
            if (groupBase != null) constraint("M06", role.metadata(), cls, "UniqueEnactment",
                    "self.oclIsTypeOf(" + cls + ") implies " + cls + ".allInstances()->select(p | p.oclIsTypeOf("
                            + cls + ") and p.agent = self.agent and p.group = self.group)->size() = 1");
        }
        for (var group : ss.groups()) {
            String cls = name(group.metadata().semanticId());
            createClass(cls, group.metadata(), "M05", false, "DOMAIN_SCHEMA");
            api.createGeneralization(cls, groupBase);
            if (roleBase != null) constraint("M30", group.metadata(), cls, "DeclaredRoleTypes", "self.players->forAll(p | "
                    + disjunction("p", group.roleSemanticIds(), true) + ")");
            // The policy is stored on its owner; no cardinality helper objects exist in AUTO.
            policy(cls, "M05", group.metadata(), Map.of("localId", group.groupId(),
                    "parent", group.parentGroupSemanticId(), "roles", group.roleSemanticIds()));
        }
        for (var card : ss.groupRoleCardinalities()) {
            String count = "self.players->select(p | p.oclIsTypeOf(" + name(card.roleId()) + "))->size()";
            // Pinned GroupInstance.getPlayers(roleId, ...) counts exact roles, NOT entailed subroles.
            cardinality("M15", card.metadata(), name(card.groupId()), count, card.min(), card.max());
        }
        for (var card : ss.subGroupCardinalities()) {
            String navigation = "subgroups_" + MoiseUseSymbols.hash(card.metadata().semanticId());
            association("M23", card.metadata(), name(card.parentGroupId()),
                    "parent_" + MoiseUseSymbols.hash(card.metadata().semanticId()), "1", name(card.subGroupId()),
                    navigation, upperMultiplicity(card.max()), true);
            cardinality("M16", card.metadata(), name(card.parentGroupId()), count(navigation, card.max()), card.min(), card.max());
            record("M31", card.metadata(), "CardinalityOwnerEndpoint", "class:" + name(card.parentGroupId()), "EXACT_PARENT=" + card.parentGroupId());
            record("M32", card.metadata(), "CardinalityMemberEndpoint", "class:" + name(card.subGroupId()), "EXACT_CHILD=" + card.subGroupId());
        }
        for (var relation : ss.roleRelations()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("kind", relation.relationKind()); fields.put("group", relation.groupSemanticId());
            fields.put("sourceRole", relation.sourceRoleSemanticId()); fields.put("targetRole", relation.targetRoleSemanticId());
            fields.put("scope", relation.scope()); fields.put("extendsToSubGroups", relation.extendsToSubGroups());
            fields.put("bidirectional", relation.bidirectional());
            policy(os, "M07", relation.metadata(), fields);
            if (!relation.sourceRoleSemanticId().isEmpty()) name(relation.sourceRoleSemanticId());
            if (!relation.targetRoleSemanticId().isEmpty()) name(relation.targetRoleSemanticId());
            name(relation.groupSemanticId());
        }
        for (var link : ss.links()) {
            policy(os, "M08", link.metadata(), Map.of("roleRelation", link.roleRelationSemanticId(), "linkType", link.linkType()));
            var relation = ss.roleRelations().stream().filter(r -> r.metadata().semanticId().equals(link.roleRelationSemanticId())).findFirst().orElseThrow();
            policyRoleEndpoint("M25", link.metadata(), relation.sourceRoleSemanticId());
            policyRoleEndpoint("M26", link.metadata(), relation.targetRoleSemanticId());
        }
        for (var compatibility : ss.compatibilities()) {
            policy(os, "M09", compatibility.metadata(), Map.of("roleRelation", compatibility.roleRelationSemanticId()));
            var relation = ss.roleRelations().stream().filter(r -> r.metadata().semanticId().equals(compatibility.roleRelationSemanticId())).findFirst().orElseThrow();
            policyRoleEndpoint("M27", compatibility.metadata(), relation.sourceRoleSemanticId());
            policyRoleEndpoint("M28", compatibility.metadata(), relation.targetRoleSemanticId());
        }
        for (var scheme : fs.schemes()) {
            String cls = name(scheme.metadata().semanticId());
            createClass(cls, scheme.metadata(), "M10", false, "DOMAIN_SCHEMA"); api.createGeneralization(cls, schemeBase);
            policy(cls, "M35", scheme.metadata(), Map.of("localId", scheme.schemeId(), "rootGoal", scheme.rootGoalSemanticId()));
            for (var goal : scheme.goals()) {
                if (!goal.schemeSemanticId().equals(scheme.metadata().semanticId())) throw badReference(goal.metadata().semanticId());
                String goalClass = name(goal.metadata().semanticId());
                createClass(goalClass, goal.metadata(), "M12", false, "DOMAIN_SCHEMA");
                api.createAttribute(goalClass, "semanticId", "String"); api.createAttribute(goalClass, "satisfied", "Boolean");
                if (!NativeUseModelBuilder.MOISE_GOAL_TYPES.contains(goal.goalType()))
                    throw new IllegalArgumentException("MOISE_GOAL_TYPE_UNSUPPORTED:" + goal.goalType());
                api.createAttribute(goalClass, "goalType", "MoiseGoalType").setDeriveExpression(
                        new ExpConstEnum(api.getModel().enumType("MoiseGoalType"), goal.goalType()));
                api.createAttribute(goalClass, "minAgentsToSatisfy", "Integer").setDeriveExpression(integerConstant(goal.minAgentsToSatisfy()));
                policy(goalClass, "M12", goal.metadata(), Map.of("localId", goal.goalId(), "description", goal.description(),
                        "arguments", goal.arguments(), "ttf", goal.ttf(), "location", goal.location(), "dependencies", goal.dependencySemanticIds(),
                        "plan", goal.planSemanticId(), "inPlan", goal.inPlanSemanticId()));
                association("M35", goal.metadata(), cls, "scheme", "1", goalClass,
                        "goal_" + MoiseUseSymbols.hash(goal.metadata().semanticId()), "1", true);
            }
            for (var mission : scheme.missions()) {
                if (!mission.schemeSemanticId().equals(scheme.metadata().semanticId())) throw badReference(mission.metadata().semanticId());
                String missionClass = name(mission.metadata().semanticId());
                createClass(missionClass, mission.metadata(), "M11", false, "DOMAIN_SCHEMA"); api.createGeneralization(missionClass, missionBase);
                constraint("M34", mission.metadata(), missionClass, "SchemeType", "self.scheme.oclIsTypeOf(" + cls + ")");
                constraint("M11", mission.metadata(), missionClass, "UniqueCommitment", missionClass
                        + ".allInstances()->select(p | p.agent = self.agent and p.scheme = self.scheme)->size() = 1");
                for (String goalId : mission.goalSemanticIds()) {
                    String navigation = "goal_" + MoiseUseSymbols.hash(goalId);
                    association("M38", mission.metadata(), missionClass,
                            "commitments_" + MoiseUseSymbols.hash(mission.metadata().semanticId()), "*",
                            name(goalId), navigation, "1", false);
                    constraint("M38", mission.metadata(), missionClass, "SameScheme_" + MoiseUseSymbols.hash(goalId),
                            "self." + navigation + ".scheme = self.scheme");
                }
                policy(missionClass, "M11", mission.metadata(), Map.of("localId", mission.missionId(),
                        "scheme", mission.schemeSemanticId(), "goals", mission.goalSemanticIds()));
            }
            if (missionBase != null) constraint("M37", scheme.metadata(), cls, "DeclaredMissionTypes",
                    "self.commitments->forAll(p | " + disjunction("p", scheme.missions().stream()
                            .map(m -> m.metadata().semanticId()).toList(), true) + ")");
            for (var plan : scheme.plans()) {
                if (!plan.schemeSemanticId().equals(scheme.metadata().semanticId())) throw badReference(plan.metadata().semanticId());
                String target = name(plan.targetGoalSemanticId());
                if (!NativeUseModelBuilder.MOISE_PLAN_OPERATORS.contains(plan.operator()))
                    throw new IllegalArgumentException("MOISE_PLAN_OPERATOR_UNSUPPORTED:" + plan.operator());
                api.createAttribute(target, "planOperator", "MoisePlanOperator").setDeriveExpression(
                        new ExpConstEnum(api.getModel().enumType("MoisePlanOperator"), plan.operator()));
                api.createAttribute(target, "planSuccessRate", "Real").setDeriveExpression(realConstant(plan.successRate()));
                policy(target, "M13", plan.metadata(), Map.of("operator", plan.operator(), "successRate", plan.successRate(),
                        "targetGoal", plan.targetGoalSemanticId(), "orderedSubGoals", plan.orderedSubGoalSemanticIds()));
                record("M39", plan.metadata(), "MAttribute", "attribute:" + target + ".planOperator", "EXACT_TARGET_GOAL=" + plan.targetGoalSemanticId());
                for (int ordinal = 0; ordinal < plan.orderedSubGoalSemanticIds().size(); ordinal++) {
                    String childId = plan.orderedSubGoalSemanticIds().get(ordinal);
                    String navigation = "subgoal_" + ordinal + "_" + MoiseUseSymbols.hash(plan.metadata().semanticId());
                    var association = association("M40", plan.metadata(), target, "parentGoal_" + ordinal + "_"
                            + MoiseUseSymbols.hash(plan.metadata().semanticId()), "1", name(childId), navigation, "1", false);
                    association.addAnnotation(annotation("MoisePlanOrder", Map.of("ordinal", Integer.toString(ordinal), "operator", plan.operator())));
                    constraint("M40", plan.metadata(), target, "SameScheme_" + ordinal,
                            "self." + navigation + ".scheme = self.scheme");
                }
            }
            if (!scheme.rootGoalSemanticId().isEmpty()) name(scheme.rootGoalSemanticId());
            for (var goal : scheme.goals()) for (String dependency : goal.dependencySemanticIds()) {
                String navigation = "dependency_" + MoiseUseSymbols.hash(dependency);
                association("M12", goal.metadata(), name(goal.metadata().semanticId()),
                        "dependents_" + MoiseUseSymbols.hash(goal.metadata().semanticId()), "*", name(dependency), navigation, "1", false);
            }
        }
        for (var card : fs.schemeMissionCardinalities()) {
            cardinality("M17", card.metadata(), name(card.schemeId()),
                    "self.commitments->select(p | p.oclIsTypeOf(" + name(card.missionId()) + "))->size()", card.min(), card.max());
            record("M36", card.metadata(), "CardinalityOwnerEndpoint", "class:" + name(card.schemeId()), "EXACT_SCHEME=" + card.schemeId());
            record("M37", card.metadata(), "CardinalityMemberEndpoint", "class:" + name(card.missionId()), "EXACT_MISSION=" + card.missionId());
        }
        for (var norm : ns.norms()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("localId", norm.normId()); fields.put("role", norm.roleSemanticId()); fields.put("mission", norm.missionSemanticId());
            fields.put("roleClass", norm.roleSemanticId().isEmpty() ? "" : name(norm.roleSemanticId()));
            fields.put("missionClass", norm.missionSemanticId().isEmpty() ? "" : name(norm.missionSemanticId()));
            fields.put("type", norm.operationType()); fields.put("condition", norm.condition()); fields.put("deadline", norm.timeConstraint());
            fields.put("normativeSpecification", norm.normativeSpecificationSemanticId());
            fields.put("verification", "EVIDENCE_ONLY_DEONTIC_TEMPORAL_SEMANTICS_NOT_OCL");
            policy(os, "M14", norm.metadata(), fields);
            record("M41", norm.metadata(), "NormPolicyMembership", policyIdentity(os, "M14", norm.metadata()),
                    "EXACT_NS=" + norm.normativeSpecificationSemanticId());
            policyRoleEndpoint("M42", norm.metadata(), norm.roleSemanticId());
            if (!norm.missionSemanticId().isEmpty()) record("M43", norm.metadata(), "NormPolicyMission",
                    "class:" + name(norm.missionSemanticId()), "EXACT_MISSION=" + norm.missionSemanticId());
        }
    }

    private void base(String cls, SemanticMetadata source, String rule, boolean wellFormed) throws UseApiException {
        createClass(cls, source, rule, true, "SUPPORTING_ENACTMENT_IDENTITY");
        api.createAttribute(cls, "semanticId", "String");
        if (wellFormed) api.createAttribute(cls, "wellFormed", "Boolean");
    }

    private void createClass(String cls, SemanticMetadata source, String rule, boolean abstractType, String representation) throws UseApiException {
        var target = api.createClass(cls, abstractType); declarations.put(cls, source);
        target.addAnnotation(annotation(ANNOTATION, Map.of("ruleId", rule, "representation", representation,
                "semanticId64", encode(source.semanticId()), "runtimeSource", "moise", "runtimeProjection", "EVIDENCE_ONLY")));
    }

    private org.tzi.use.uml.mm.MAssociation association(String rule, SemanticMetadata source,
            String firstClass, String firstRole, String firstMultiplicity, String secondClass, String secondRole,
            String secondMultiplicity, boolean composition) throws UseApiException {
        String associationName = rule + "_" + MoiseUseSymbols.hash(source.semanticId() + "|" + firstClass + "|" + firstRole + "|" + secondClass + "|" + secondRole);
        var target = api.createAssociation(associationName, new String[] { firstClass, secondClass },
                new String[] { firstRole, secondRole }, new String[] { firstMultiplicity, secondMultiplicity },
                new int[] { composition ? MAggregationKind.COMPOSITION : MAggregationKind.NONE, MAggregationKind.NONE },
                new boolean[] { false, false }, new String[0][][]);
        target.addAnnotation(annotation(ANNOTATION, Map.of("ruleId", rule, "semanticId64", encode(source.semanticId()))));
        record(rule, source, "MAssociation", "association:" + associationName, "DOMAIN_SCHEMA");
        return target;
    }

    private void cardinality(String rule, SemanticMetadata source, String context, String count, int min, int max) {
        if (max != -1 && max != Integer.MAX_VALUE) constraint(rule, source, context, "Maximum", count + " <= " + max);
        if (min > 0) constraint(rule, source, context, "MinimumWhenWellFormed",
                "(if self.wellFormed.oclIsUndefined() then false else self.wellFormed endif) implies " + count + " >= " + min);
        policy(context, rule, source, Map.of("min", min, "max", max, "lowerBoundGuard", "wellFormed"));
    }

    private void constraint(String rule, SemanticMetadata source, String context, String suffix, String ocl) {
        String id = rule + "_" + MoiseUseSymbols.hash(source.semanticId()) + "_" + suffix;
        constraints.add(new NativeConstraintSpec(id, context, ocl, List.of(rule), List.of("moise.runtime.materialized"),
                Fidelity.EXACT, "OFFICIAL_MOISE_FORMATION_SCHEMA", ConstraintMigrationStatus.NATIVE));
        record(rule, source, "MClassInvariant", "invariant:" + context + "::" + id, "FORMATION_NOT_NORMATIVE_COMPLIANCE");
    }

    private void container(SemanticMetadata source, String rule, String os) {
        policy(os, rule, source, Map.of("representation", "SPECIFICATION_CONTAINER_TRACE_ONLY"));
    }

    private void policy(String owner, String rule, SemanticMetadata source, Map<String, ?> values) {
        Map<String, Object> payload = new LinkedHashMap<>(values);
        payload.put("sourceSemanticId", source.semanticId()); payload.put("sourceDiagnostics", source.diagnostics()); payload.put("rule", rule);
        // MMPrintVisitor does not escape annotation values. Encoding arbitrary ids/text is lossless
        // and prevents quotes, backslashes, or multiline conditions from corrupting .use export.
        String annotation = "MoisePolicy_" + rule + "_" + MoiseUseSymbols.hash(source.semanticId());
        api.getModel().getClass(owner).addAnnotation(new MElementAnnotation(annotation, Map.of("payload64",
                Base64.getUrlEncoder().withoutPadding().encodeToString(CanonicalJson.encode(payload)))));
        record(rule, source, "MElementAnnotation", "class:" + owner + "@" + annotation, "STATIC_POLICY_NOT_RUNTIME_OBJECT");
    }

    private void record(String rule, SemanticMetadata source, String kind, String identity, String diagnostic) {
        trace.add(catalog.require(rule), TracePhase.MODEL_DECLARATION, source, kind, identity, List.of(diagnostic));
    }

    private static MElementAnnotation annotation(String name, Map<String, String> values) {
        return new MElementAnnotation(name, new java.util.TreeMap<>(values));
    }

    private void policyRoleEndpoint(String rule, SemanticMetadata source, String roleId) {
        if (!roleId.isEmpty()) record(rule, source, "PolicyRoleEndpoint", "class:" + name(roleId), "EXACT_ROLE=" + roleId);
    }

    private static String policyIdentity(String owner, String rule, SemanticMetadata source) {
        return "class:" + owner + "@MoisePolicy_" + rule + "_" + MoiseUseSymbols.hash(source.semanticId());
    }

    private String disjunction(String variable, List<String> identities, boolean exact) {
        return identities.isEmpty() ? "false" : identities.stream().map(id -> variable + (exact ? ".oclIsTypeOf(" : ".oclIsKindOf(")
                + name(id) + ")").collect(java.util.stream.Collectors.joining(" or "));
    }

    private String name(String id) {
        String result = symbols.get(id);
        if (result == null) throw badReference(id);
        return result;
    }

    private static IllegalArgumentException badReference(String id) { return new IllegalArgumentException("MOISE_EXACT_SCHEMA_REFERENCE_MISSING:" + id); }

    /** Typed, owner-scoped foreign keys; a globally present symbol is not sufficient evidence. */
    private static void validate(OrganizationSemantic org) {
        var ss = org.structuralSpecification(); var fs = org.functionalSpecification(); var ns = org.normativeSpecification();
        String osId = org.metadata().semanticId();
        if (!ss.organizationSemanticId().equals(osId) || !fs.organizationSemanticId().equals(osId)
                || !ns.organizationSemanticId().equals(osId)) throw badReference(osId);
        var roles = ss.roles().stream().collect(java.util.stream.Collectors.toMap(r -> r.metadata().semanticId(), r -> r));
        var groups = ss.groups().stream().collect(java.util.stream.Collectors.toMap(g -> g.metadata().semanticId(), g -> g));
        if (!ss.rootGroupSemanticId().isEmpty() && !groups.containsKey(ss.rootGroupSemanticId())) throw badReference(ss.rootGroupSemanticId());
        for (var role : ss.roles()) for (String parent : role.superRoleSemanticIds())
            if (!roles.containsKey(parent)) throw badReference(parent);
        for (var group : ss.groups()) {
            if (!group.parentGroupSemanticId().isEmpty() && (!groups.containsKey(group.parentGroupSemanticId())
                    || !groups.get(group.parentGroupSemanticId()).subgroupSemanticIds().contains(group.metadata().semanticId())))
                throw badReference(group.parentGroupSemanticId());
            for (String child : group.subgroupSemanticIds()) if (!groups.containsKey(child)
                    || !groups.get(child).parentGroupSemanticId().equals(group.metadata().semanticId())) throw badReference(child);
            for (String role : group.roleSemanticIds()) if (!roles.containsKey(role)) throw badReference(role);
        }
        Set<String> tuples = new TreeSet<>();
        for (var card : ss.groupRoleCardinalities()) {
            if (!groups.containsKey(card.groupId()) || !groups.get(card.groupId()).roleSemanticIds().contains(card.roleId())) throw badReference(card.metadata().semanticId());
            if (!tuples.add(card.groupId() + "\u0000" + card.roleId())) throw new IllegalArgumentException("MOISE_DUPLICATE_CARDINALITY_TUPLE:" + card.metadata().semanticId());
        }
        tuples.clear();
        for (var card : ss.subGroupCardinalities()) {
            if (!groups.containsKey(card.parentGroupId()) || !groups.get(card.parentGroupId()).subgroupSemanticIds().contains(card.subGroupId())) throw badReference(card.metadata().semanticId());
            if (!tuples.add(card.parentGroupId() + "\u0000" + card.subGroupId())) throw new IllegalArgumentException("MOISE_DUPLICATE_CARDINALITY_TUPLE:" + card.metadata().semanticId());
        }
        Set<String> allMissions = new TreeSet<>();
        Map<String, Set<String>> missionsByScheme = new LinkedHashMap<>();
        for (var scheme : fs.schemes()) {
            if (!scheme.functionalSpecificationSemanticId().equals(fs.metadata().semanticId())) throw badReference(scheme.metadata().semanticId());
            Set<String> goals = scheme.goals().stream().map(g -> g.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());
            Set<String> plans = scheme.plans().stream().map(p -> p.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());
            if (!scheme.rootGoalSemanticId().isEmpty() && !goals.contains(scheme.rootGoalSemanticId())) throw badReference(scheme.rootGoalSemanticId());
            var missions = scheme.missions().stream().map(m -> m.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());
            missionsByScheme.put(scheme.metadata().semanticId(), missions); allMissions.addAll(missions);
            for (var mission : scheme.missions()) for (String goal : mission.goalSemanticIds()) if (!goals.contains(goal)) throw badReference(goal);
            for (var goal : scheme.goals()) {
                for (String dependency : goal.dependencySemanticIds()) if (!goals.contains(dependency)) throw badReference(dependency);
                if (!goal.planSemanticId().isEmpty() && !plans.contains(goal.planSemanticId())) throw badReference(goal.planSemanticId());
                if (!goal.inPlanSemanticId().isEmpty() && !plans.contains(goal.inPlanSemanticId())) throw badReference(goal.inPlanSemanticId());
            }
            for (var plan : scheme.plans()) {
                if (!goals.contains(plan.targetGoalSemanticId())) throw badReference(plan.targetGoalSemanticId());
                for (String child : plan.orderedSubGoalSemanticIds()) if (!goals.contains(child)) throw badReference(child);
            }
        }
        tuples.clear();
        for (var card : fs.schemeMissionCardinalities()) {
            if (!missionsByScheme.containsKey(card.schemeId()) || !missionsByScheme.get(card.schemeId()).contains(card.missionId())) throw badReference(card.metadata().semanticId());
            if (!tuples.add(card.schemeId() + "\u0000" + card.missionId())) throw new IllegalArgumentException("MOISE_DUPLICATE_CARDINALITY_TUPLE:" + card.metadata().semanticId());
        }
        for (var norm : ns.norms()) {
            if (!norm.normativeSpecificationSemanticId().equals(ns.metadata().semanticId())) throw badReference(norm.metadata().semanticId());
            if (!norm.roleSemanticId().isEmpty() && !roles.containsKey(norm.roleSemanticId())) throw badReference(norm.roleSemanticId());
            if (!norm.missionSemanticId().isEmpty() && !allMissions.contains(norm.missionSemanticId())) throw badReference(norm.missionSemanticId());
        }
    }
    private String supportName(OrganizationSemantic org, String kind) { return symbols.get(supportId(org, kind)); }
    private static String supportId(OrganizationSemantic org, String kind) { return org.metadata().semanticId() + ":use-support:" + kind; }
    private static void support(List<MoiseUseSymbols.Candidate> candidates, OrganizationSemantic org, String kind) {
        candidates.add(new MoiseUseSymbols.Candidate(supportId(org, kind), "Support", org.name() + "_" + kind));
    }
    private static void candidate(List<MoiseUseSymbols.Candidate> candidates, SemanticMetadata source, String kind, String label) {
        candidates.add(new MoiseUseSymbols.Candidate(source.semanticId(), kind, label));
    }
    private static String encode(String text) { return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    private static String upperMultiplicity(int max) { return max == -1 || max == Integer.MAX_VALUE ? "*" : "0.." + max; }
    private static String count(String navigation, int max) {
        return max == 0 || max == 1 ? "(if self." + navigation + ".oclIsUndefined() then 0 else 1 endif)" : "self." + navigation + "->size()";
    }

    // Match USE's compiler AST for signed literals (Moise -1 means "all agents").
    // Do not weaken export signatures by ignoring whitespace or string-literal contents.
    private static Expression integerConstant(int value) {
        if (value >= 0) return new ExpConstInteger(value);
        try { return ExpStdOp.create("-", new Expression[] { new ExpConstInteger(-value) }); }
        catch (org.tzi.use.uml.ocl.expr.ExpInvalidException error) { throw new IllegalArgumentException(error); }
    }
    private static Expression realConstant(double value) {
        if (Double.doubleToRawLongBits(value) >= 0) return new ExpConstReal(value);
        try { return ExpStdOp.create("-", new Expression[] { new ExpConstReal(-value) }); }
        catch (org.tzi.use.uml.ocl.expr.ExpInvalidException error) { throw new IllegalArgumentException(error); }
    }
}
