package org.tzi.use.plugins.jacamo.codegrounded.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;

/**
 * Complete deterministic registry for J01-J11, A01-A22, C01-C20, M01-M43 and X01-X09.
 * A catalog entry describes the intended contract even when the audited API cannot yet supply it.
 */
public final class CodeGroundedRuleCatalog {
    /** Version of the code-grounded rule contract exported with native evidence. */
    public static final String VERSION = "1.1.0";

    private static final Set<String> FIRST_SLICE = Set.of(
            "J01", "J02", "J03", "J04", "J05", "J06", "J07", "J08", "J09", "J10", "J11",
            "A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08", "A09", "A10", "A11",
            "A16", "A17", "A18", "A19", "A20", "A21", "A22",
            "C01", "C02", "C03", "C04", "C05", "C07", "C09", "C10", "C11", "C12",
            "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20",
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11",
            "M12", "M13", "M14", "M15", "M16", "M17", "M18", "M19", "M20", "M21", "M22",
            "M23", "M24", "M25", "M26", "M27", "M28", "M29", "M30", "M31", "M32", "M33",
            "M34", "M35", "M36", "M37", "M38", "M39", "M40", "M41", "M42", "M43",
            "X01", "X02", "X03", "X04", "X05", "X06", "X07", "X08", "X09");
    private static final List<CodeGroundedRule> RULES = build();
    private static final Map<String, CodeGroundedRule> BY_ID = index(RULES);

    public List<CodeGroundedRule> rules() { return RULES; }

    public CodeGroundedRule require(String ruleId) {
        CodeGroundedRule rule = BY_ID.get(ruleId);
        if (rule == null) throw new IllegalArgumentException("CODE_GROUNDED_RULE_UNKNOWN: " + ruleId);
        return rule;
    }

    public Map<String, CodeGroundedRule> byId() { return BY_ID; }

    private static List<CodeGroundedRule> build() {
        List<CodeGroundedRule> rules = new ArrayList<>(105);
        addFamily(rules, 'J', RuleDimension.JCM, EvidenceAuthority.OFFICIAL_JACAMO_API,
                new String[] {
                        "jacamo.project.JaCaMoProject", "jacamo.project.JaCaMoAgentParameters",
                        "jacamo.project.JaCaMoWorkspaceParameters", "jason.mas2j.ClassParameters",
                        "jacamo.project.JaCaMoOrgParameters", "jacamo.project.JaCaMoGroupParameters",
                        "jacamo.project.JaCaMoSchemeParameters", "jacamo.project.JaCaMoInstParameters",
                        "jacamo.project.JaCaMoAgentParameters.roles", "jacamo.project.JaCaMoAgentParameters.focus",
                        "jacamo.project.parser.JaCaMoProjectParserTokenManager"
                },
                new String[] {
                        "MModel", "MClass Agent", "MClass WorkspaceDeclaration", "MClass ArtifactDeclaration",
                        "MClass OrganizationDeployment", "MClass GroupDeployment", "MClass SchemeDeployment",
                        "MClass InstitutionDeployment", "Trace raw role tuple", "Trace raw focus tuple",
                        "Trace import provenance"
                });
        addFamily(rules, 'A', RuleDimension.JASON, EvidenceAuthority.OFFICIAL_JASON_API,
                new String[] {
                        "jason.asSemantics.Agent", "jason.pl.PlanLibrary", "jason.asSyntax.Plan",
                        "jason.asSyntax.Trigger", "jason.asSyntax.PlanBody", "jason.asSyntax.PlanBody.BodyType.action",
                        "jason.asSyntax.PlanBody.BodyType.internalAction", "jason.asSyntax.Literal",
                        "jason.asSemantics.Agent.getInitialGoals", "jason.asSyntax.Rule", "jason.asSyntax.SourceInfo",
                        "jason.asSemantics.ActionExec", "jason.asSemantics.Intention", "jason.asSemantics.Event",
                        "jason.asSemantics.TransitionSystem", "jason.asSemantics.Agent.getPL",
                        "jason.pl.PlanLibrary.getPlans", "jason.asSyntax.Plan.getTrigger",
                        "jason.asSyntax.Plan.getBody", "jason.asSyntax.PlanBody.getBodyNext",
                        "jason.asSemantics.Agent.getBB", "jason.asSemantics.Agent.getInitialGoals"
                },
                new String[] {
                        "MClass AgentProgram", "MClass PlanLibrary", "MClass Plan", "MClass Trigger",
                        "MClass PlanBodyElement", "MClass Action", "MClass Action", "MClass Belief",
                        "MClass AgentGoal", "MClass BeliefRule", "Trace source provenance", "Trace runtime evidence (EVIDENCE_ONLY)",
                        "Trace runtime evidence (EVIDENCE_ONLY)", "Trace runtime evidence (EVIDENCE_ONLY)", "Trace runtime evidence (EVIDENCE_ONLY)",
                        "MAssociation A16AgentProgramPlanLibrary", "MAssociation A17PlanLibraryPlan",
                        "MAssociation A18PlanTrigger", "MAssociation A19PlanBodyElement",
                        "MAssociation A20PlanBodyNext", "MAssociation A21ProgramBelief",
                        "MAssociation A22ProgramGoal"
                });
        addFamily(rules, 'C', RuleDimension.CARTAGO, EvidenceAuthority.OFFICIAL_CARTAGO_API,
                new String[] {
                        "cartago.CartagoEnvironment", "cartago.WorkspaceDescriptor", "java.lang.Class<cartago.Artifact>",
                        "cartago.ArtifactId", "cartago.OpDescriptor", "java.lang.reflect.Method",
                        "cartago.IArtifactGuard", "cartago.ObsProperty", "cartago.ArtifactObsProperty", "cartago.ArtifactInfo",
                        "cartago.Tuple", "cartago.AgentId", "cartago.CartagoEnvironment.getRootWSP",
                        "cartago.ArtifactId.getWorkspaceId", "cartago.ArtifactId.getArtifactType",
                        "cartago.OpDescriptor", "cartago.ArtifactInfo.getObsProperties", "cartago.OpDescriptor.getGuard",
                        "cartago.ICartagoController.getCurrentAgents", "cartago.ICartagoLogger.artifactFocussed"
                },
                new String[] {
                        "MClass Environment", "MClass Workspace", "MClass ArtifactType", "MClass Artifact",
                        "MClass Operation", "MOperation", "MClass Guard", "MClass LiveObservableProperty",
                        "MClass ObservablePropertySnapshot", "MClass ArtifactInfo", "MClass Signal", "MClass CartagoAgentIdentity",
                        "MAssociation C13EnvironmentWorkspace", "MAssociation C14WorkspaceArtifact",
                        "MAssociation C15ArtifactType", "MAssociation C16ArtifactOperation",
                        "MAssociation C17ArtifactObservableProperty", "MAssociation C18OperationGuard",
                        "MAssociation C19WorkspaceAgent", "MAssociation C20AgentArtifactFocus"
                });
        addFamily(rules, 'M', RuleDimension.MOISE, EvidenceAuthority.OFFICIAL_MOISE_API,
                new String[] {
                        "moise.os.OS", "moise.os.ss.SS", "moise.os.fs.FS", "moise.os.ns.NS", "moise.os.ss.Group",
                        "moise.os.ss.Role", "moise.os.ss.RoleRel", "moise.os.ss.Link", "moise.os.ss.Compatibility",
                        "moise.os.fs.Scheme", "moise.os.fs.Mission", "moise.os.fs.Goal", "moise.os.fs.Plan",
                        "moise.os.ns.Norm", "moise.os.Cardinality (Group.getRoleCardinality)", "moise.os.Cardinality (Group.getSubGroupCardinality)",
                        "moise.os.Cardinality (Scheme.getMissionCardinality)", "moise.os.OS.getSS", "moise.os.OS.getFS", "moise.os.OS.getNS",
                        "moise.os.ss.SS.getRolesDef", "moise.os.ss.SS.getRootGrSpec", "moise.os.ss.Group.getSubGroups",
                        "moise.os.ss.Role.getSuperRoles", "moise.os.ss.Link.getSource", "moise.os.ss.Link.getTarget",
                        "moise.os.ss.Compatibility.getSource", "moise.os.ss.Compatibility.getTarget",
                        "moise.os.ss.Group.getRoleCardinality (owner)", "moise.os.ss.Group.getRoles (member)",
                        "moise.os.ss.Group.getSubGroupCardinality (owner)", "moise.os.ss.Group.getSubGroups (member)", "moise.os.fs.FS.getSchemes",
                        "moise.os.fs.Scheme.getMissions", "moise.os.fs.Scheme.getRootGoal",
                        "moise.os.fs.Scheme.getMissionCardinality (owner)", "moise.os.fs.Scheme.getMissions (member)", "moise.os.fs.Mission.getGoals",
                        "moise.os.fs.Goal.getPlan", "moise.os.fs.Plan.getSubGoals", "moise.os.ns.NS.getNorms",
                        "moise.os.ns.Norm.getRole", "moise.os.ns.Norm.getMission"
                },
                new String[] {
                        "MClass <OS.id> (domain schema; FULL: Organization inspection)",
                        "Trace SS container (FULL: StructuralSpecification inspection)",
                        "Trace FS container (FULL: FunctionalSpecification inspection)",
                        "Trace NS container (FULL: NormativeSpecification inspection)",
                        "MClass <Group.id> (group-instance type; FULL: Group inspection)",
                        "MClass <Role.id> (role-enactment type; FULL: Role inspection)",
                        "Trace role-relation policy (FULL: RoleRelation inspection)",
                        "Trace link policy (FULL: Link inspection)", "Trace compatibility policy (FULL: Compatibility inspection)",
                        "MClass <Scheme.id> (scheme-instance type; FULL: Scheme inspection)",
                        "MClass <Scheme.id>_<Mission.id> (commitment type; FULL: Mission inspection)",
                        "MClass <Scheme.id>_<Goal.id> (goal-state type; FULL: OrganizationalGoal inspection)",
                        "MAttribute planOperator/planSuccessRate + Trace ordered plan policy (FULL: OrganizationalPlan inspection)",
                        "Trace norm policy, exact Role/Mission endpoints; temporal/deontic OCL unavailable (FULL: Norm inspection)",
                        "MClassInvariant per Group-Role cardinality (FULL: GroupRoleCardinality inspection)",
                        "MAssociation + MClassInvariant per parent-subgroup cardinality (FULL: SubGroupCardinality inspection)",
                        "MClassInvariant per Scheme-Mission cardinality (FULL: SchemeMissionCardinality inspection)",
                        "Trace OS-SS container", "Trace OS-FS container", "Trace OS-NS container", "Trace SS-role type membership",
                        "MAssociation organization-group instances", "MAssociation typed parent-subgroup composition",
                        "MGeneralization role entailment (not Agent inheritance)",
                        "Trace link-source role", "Trace link-target role", "Trace compatibility-source role", "Trace compatibility-target role",
                        "MAssociation GroupInstance-RoleEnactment", "MClassInvariant declared/exact role types",
                        "Trace subgroup cardinality owner", "Trace subgroup cardinality member",
                        "MAssociation organization-scheme composition", "MAssociation scheme-commitment composition",
                        "MAssociation scheme-goal composition + Trace root-goal identity",
                        "Trace scheme cardinality owner", "MClassInvariant declared mission types",
                        "MAssociation commitment-goal (same scheme)", "MAttribute target-goal plan operator + Trace plan identity",
                        "MAssociation typed subgoal edges + ordinal/operator annotations",
                        "Trace NS-norm policy membership", "Trace norm-role type endpoint", "Trace norm-mission type endpoint"
                });
        addFamily(rules, 'X', RuleDimension.CROSS, EvidenceAuthority.EXPLICIT_BINDING,
                new String[] {
                        "jason.asSyntax.PlanBody+cartago.OpDescriptor", "jason.asSyntax.Literal+cartago.ArtifactObsProperty (C09 snapshot)",
                        "jason.asSyntax.Trigger+cartago.Tuple", "jacamo.project.JaCaMoAgentParameters.roles+moise.os.ss.Role",
                        "jacamo.project.JaCaMoAgentParameters+cartago.WorkspaceId",
                        "jacamo.project.JaCaMoAgentParameters.focus+cartago.ArtifactId",
                        "jason.asSemantics.Event+moise.os.fs.Goal", "jason.mas2j.ClassParameters+cartago.ArtifactId",
                        "jacamo.project.JaCaMoAgentParameters+jason.asSemantics.Agent+cartago.AgentId"
                },
                new String[] {
                        "MAssociation X01ActionOperation", "MAssociation X02BeliefProperty",
                        "MAssociation X03TriggerSignal", "MAssociation Agent-role enactment (FULL: X04AgentRole inspection)",
                        "MAssociation X05AgentWorkspace", "MAssociation X06AgentArtifactFocus",
                        "MAssociation X07AgentGoalOrganizationalGoal", "MAssociation X08DeclarationArtifact",
                        "MAssociation X09AgentIdentity"
                });
        if (rules.size() != 105) throw new IllegalStateException("CODE_GROUNDED_RULE_COUNT: " + rules.size());
        return List.copyOf(rules);
    }

    private static void addFamily(List<CodeGroundedRule> rules, char prefix, RuleDimension dimension,
                                  EvidenceAuthority authority, String[] sources, String[] targets) {
        if (sources.length != targets.length) throw new IllegalStateException("RULE_FAMILY_METADATA_MISMATCH: " + prefix);
        for (int index = 0; index < sources.length; index++) {
            String id = "%c%02d".formatted(prefix, index + 1);
            EvidenceAuthority ruleAuthority = id.equals("J11") ? EvidenceAuthority.OFFICIAL_GENERATED_LEXER : authority;
            Fidelity fidelity = fidelity(id);
            CapabilityStatus capability = capability(id);
            ImplementationStatus implementation = implementation(id);
            String policy = switch (implementation) {
                case IMPLEMENTED -> "FAIL_CLOSED_ON_CONTRACT_OR_API_DRIFT";
                case UNAVAILABLE_IN_AUDITED_API -> "EMIT_UNAVAILABLE_DIAGNOSTIC_AND_PRESERVE_UNKNOWN";
                case EXPLICITLY_UNSUPPORTED -> "EMIT_EXPLICITLY_UNSUPPORTED_DIAGNOSTIC";
                case PLANNED_CAPABILITY_GATED -> "SKIP_WITH_CAPABILITY_DIAGNOSTIC";
            };
            rules.add(new CodeGroundedRule(id, dimension, ruleAuthority, sources[index], targets[index], fidelity,
                    capability, implementation, policy));
        }
    }

    private static Fidelity fidelity(String id) {
        if (id.equals("J11")) return Fidelity.PROVENANCE_ONLY;
        if (id.equals("C08")) return Fidelity.UNKNOWN;
        if (id.equals("C06")) return Fidelity.CONDITIONAL;
        if (id.matches("A1[2-5]|C0[489]|C1[0-2]|C1[479]|C20")) return Fidelity.RUNTIME_ONLY;
        if (id.startsWith("X")) return Fidelity.CONDITIONAL;
        return Fidelity.EXACT;
    }

    private static CapabilityStatus capability(String id) {
        if (id.equals("C08")) return CapabilityStatus.UNAVAILABLE;
        return FIRST_SLICE.contains(id) ? CapabilityStatus.COMPLETE : CapabilityStatus.PARTIAL;
    }

    private static ImplementationStatus implementation(String id) {
        if (FIRST_SLICE.contains(id)) return ImplementationStatus.IMPLEMENTED;
        if (id.equals("C08")) return ImplementationStatus.UNAVAILABLE_IN_AUDITED_API;
        return ImplementationStatus.PLANNED_CAPABILITY_GATED;
    }

    private static Map<String, CodeGroundedRule> index(List<CodeGroundedRule> rules) {
        Map<String, CodeGroundedRule> result = new LinkedHashMap<>();
        for (CodeGroundedRule rule : rules)
            if (result.put(rule.ruleId(), rule) != null)
                throw new IllegalStateException("CODE_GROUNDED_RULE_DUPLICATE: " + rule.ruleId());
        return Collections.unmodifiableMap(result);
    }
}
