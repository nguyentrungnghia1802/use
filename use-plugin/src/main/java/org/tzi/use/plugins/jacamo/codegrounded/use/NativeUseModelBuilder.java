package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactTypeSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.BackingJavaOperationSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.CodeGroundedConstraintPlanner;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeConstraintInstaller;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeConstraintSpec;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRule;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceIndex;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceRecord;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.uml.mm.MAggregationKind;
import org.tzi.use.uml.mm.MModel;

/** Builds the Phase-1B schema directly through {@link UseModelApi}; no .use text participates in authority. */
public final class NativeUseModelBuilder {
    public static final List<String> BODY_TYPES = List.of("none", "action", "internalAction", "achieve", "test",
            "addBel", "addBelNewFocus", "addBelBegin", "addBelEnd", "delBel", "delBelNewFocus", "delAddBel",
            "achieveNF", "constraint");
    public static final List<String> TRIGGER_OPERATORS = List.of("add", "del", "goalState");
    public static final List<String> TRIGGER_TYPES = List.of("belief", "achieve", "test", "signal");
    public static final List<String> ACTION_KINDS = List.of("EXTERNAL", "INTERNAL");
    public static final List<String> MOISE_PLAN_OPERATORS = List.of("sequence", "choice", "parallel");
    public static final List<String> MOISE_GOAL_TYPES = List.of("performance", "achievement", "maintenance");
    public static final List<String> MOISE_NORM_TYPES = List.of("obligation", "permission");
    private static final List<String> BASE_CLASSES = List.of(
            "Agent", "WorkspaceDeclaration", "ArtifactDeclaration", "OrganizationDeployment",
            "GroupDeployment", "SchemeDeployment", "InstitutionDeployment", "AgentProgram", "PlanLibrary",
            "Plan", "Trigger", "PlanBodyElement", "Action", "Belief", "AgentGoal", "BeliefRule",
            "Environment", "Workspace", "ArtifactType", "Artifact", "Operation", "BackingJavaOperation",
            "Guard", "LiveObservableProperty", "ObservablePropertySnapshot", "ArtifactInfo", "Signal",
            "CartagoAgentIdentity", "A17PlanOrderEntry", "A19BodyOrderEntry", "Organization",
            "StructuralSpecification", "FunctionalSpecification", "NormativeSpecification", "Group", "Role",
            "RoleRelation", "Link", "Compatibility", "Scheme", "Mission", "OrganizationalGoal",
            "OrganizationalPlan", "Norm", "GroupRoleCardinality", "SubGroupCardinality",
            "SchemeMissionCardinality", "ExactBindingEvidence");

    public Result build(JacamoSpecificationModel source) {
        CodeGroundedTraceCollector trace = new CodeGroundedTraceCollector();
        return build(source, trace, NativeProjectionMode.AUTO);
    }

    public Result build(JacamoSpecificationModel source, CodeGroundedTraceCollector trace) {
        return build(source, trace, NativeProjectionMode.AUTO);
    }

    public Result build(JacamoSpecificationModel source, NativeProjectionMode mode) {
        return build(source, new CodeGroundedTraceCollector(), mode);
    }

    public Result build(JacamoSpecificationModel source, CodeGroundedTraceCollector trace,
                        NativeProjectionMode mode) {
        NativeProjectionProfile profile = NativeProjectionProfile.forMode(mode);
        var catalog = new CodeGroundedRuleCatalog();
        NativeOperationPlan operationPlan = NativeOperationPlan.resolve(source);
        UseModelApi api = new UseModelApi(modelName(source.project().name()));
        try {
            api.createEnumeration("TriggerOperator", TRIGGER_OPERATORS);
            api.createEnumeration("TriggerType", TRIGGER_TYPES);
            api.createEnumeration("PlanBodyType", BODY_TYPES);
            api.createEnumeration("ActionKind", ACTION_KINDS);
            api.createEnumeration("MoisePlanOperator", MOISE_PLAN_OPERATORS);
            api.createEnumeration("MoiseGoalType", MOISE_GOAL_TYPES);
            api.createEnumeration("MoiseNormType", MOISE_NORM_TYPES);

            for (String name : BASE_CLASSES)
                if (profile.materializesClass(name)) api.createClass(name, false);
            for (String name : operationPlan.artifactTypeClassNames().values()) api.createClass(name, false);
            for (String name : operationPlan.artifactTypeClassNames().values()) api.createGeneralization(name, "Artifact");
            for (String name : BASE_CLASSES)
                if (profile.materializesClass(name)) api.createAttribute(name, "semanticId", "String");
            api.createAttribute("Agent", "name", "String");
            api.createAttribute("Agent", "sourceUri", "String");
            api.createAttribute("Agent", "options", "String");
            api.createAttribute("Agent", "architectureClasses", "String");
            api.createAttribute("Agent", "agentClass", "String");
            api.createAttribute("Agent", "beliefBaseClass", "String");
            api.createAttribute("Agent", "host", "String");
            api.createAttribute("Agent", "instances", "Integer");
            attribute(api, profile, "WorkspaceDeclaration", "name", "String");
            attribute(api, profile, "WorkspaceDeclaration", "host", "String");
            attribute(api, profile, "WorkspaceDeclaration", "debug", "Boolean");
            attribute(api, profile, "ArtifactDeclaration", "name", "String");
            attribute(api, profile, "ArtifactDeclaration", "workspace", "String");
            attribute(api, profile, "ArtifactDeclaration", "javaClass", "String");
            attribute(api, profile, "ArtifactDeclaration", "parameters", "String");
            attribute(api, profile, "OrganizationDeployment", "name", "String");
            attribute(api, profile, "OrganizationDeployment", "source", "String");
            attribute(api, profile, "OrganizationDeployment", "institution", "String");
            attribute(api, profile, "OrganizationDeployment", "debug", "String");
            attribute(api, profile, "GroupDeployment", "organization", "String");
            attribute(api, profile, "GroupDeployment", "name", "String");
            attribute(api, profile, "GroupDeployment", "type", "String");
            attribute(api, profile, "GroupDeployment", "responsibleFor", "String");
            attribute(api, profile, "SchemeDeployment", "organization", "String");
            attribute(api, profile, "SchemeDeployment", "name", "String");
            attribute(api, profile, "SchemeDeployment", "type", "String");
            attribute(api, profile, "InstitutionDeployment", "name", "String");
            attribute(api, profile, "InstitutionDeployment", "workspaces", "String");
            attribute(api, profile, "InstitutionDeployment", "opaqueParameters", "String");
            api.createAttribute("Environment", "name", "String");
            api.createAttribute("Environment", "environmentId", "String");
            api.createAttribute("Environment", "version", "String");
            api.createAttribute("Environment", "defaultInfrastructureLayer", "String");
            api.createAttribute("Workspace", "fullName", "String");
            api.createAttribute("Workspace", "name", "String");
            api.createAttribute("Workspace", "uuid", "String");
            api.createAttribute("Workspace", "parentSemanticId", "String");
            api.createAttribute("Workspace", "environmentSemanticId", "String");
            api.createAttribute("Workspace", "local", "Boolean");
            api.createAttribute("Workspace", "protocol", "String");
            api.createAttribute("Workspace", "remotePath", "String");
            api.createAttribute("Workspace", "address", "String");
            api.createAttribute("ArtifactType", "javaClassName", "String");
            api.createAttribute("ArtifactType", "classLoaderIdentity", "String");
            api.createAttribute("Artifact", "name", "String");
            api.createAttribute("Artifact", "uuid", "String");
            api.createAttribute("Artifact", "artifactTypeSemanticId", "String");
            api.createAttribute("Artifact", "workspaceSemanticId", "String");
            api.createAttribute("Artifact", "creatorAgentSemanticId", "String");
            attribute(api, profile, "Operation", "artifactSemanticId", "String");
            attribute(api, profile, "Operation", "keyId", "String");
            attribute(api, profile, "Operation", "name", "String");
            attribute(api, profile, "Operation", "arity", "Integer");
            attribute(api, profile, "Operation", "dynamic", "Boolean");
            attribute(api, profile, "Operation", "linkOperation", "Boolean");
            attribute(api, profile, "Operation", "ui", "Boolean");
            attribute(api, profile, "Operation", "internal", "Boolean");
            if (profile.materializesClass("BackingJavaOperation")) {
                api.createAttribute("BackingJavaOperation", "operationDescriptorId", "String");
                api.createAttribute("BackingJavaOperation", "declaringClass", "String");
                api.createAttribute("BackingJavaOperation", "methodName", "String");
                api.createAttribute("BackingJavaOperation", "parameterTypes", "String");
                api.createAttribute("BackingJavaOperation", "returnType", "String");
                api.createAttribute("BackingJavaOperation", "varArgs", "Boolean");
                api.createAttribute("BackingJavaOperation", "classLoaderIdentity", "String");
            }
            attribute(api, profile, "Guard", "operationDescriptorId", "String");
            attribute(api, profile, "Guard", "name", "String");
            attribute(api, profile, "Guard", "arity", "Integer");
            attribute(api, profile, "Guard", "implementationClass", "String");
            if (profile.materializesClass("LiveObservableProperty")) {
                api.createAttribute("LiveObservableProperty", "artifactSemanticId", "String");
                api.createAttribute("LiveObservableProperty", "propertyId", "String");
                api.createAttribute("LiveObservableProperty", "name", "String");
                api.createAttribute("LiveObservableProperty", "values", "String");
                api.createAttribute("LiveObservableProperty", "annotations", "String");
            }
            api.createAttribute("ObservablePropertySnapshot", "artifactSemanticId", "String");
            api.createAttribute("ObservablePropertySnapshot", "propertyId", "String");
            api.createAttribute("ObservablePropertySnapshot", "name", "String");
            api.createAttribute("ObservablePropertySnapshot", "values", "String");
            api.createAttribute("ObservablePropertySnapshot", "valueTypes", "String");
            api.createAttribute("ObservablePropertySnapshot", "annotations", "String");
            if (profile.materializesClass("ArtifactInfo")) {
                api.createAttribute("ArtifactInfo", "artifactSemanticId", "String");
                api.createAttribute("ArtifactInfo", "creatorAgentSemanticId", "String");
                api.createAttribute("ArtifactInfo", "operationSemanticIds", "String");
                api.createAttribute("ArtifactInfo", "observablePropertySemanticIds", "String");
                api.createAttribute("ArtifactInfo", "linkedArtifactSemanticIds", "String");
            }
            attribute(api, profile, "Signal", "artifactSemanticId", "String");
            attribute(api, profile, "Signal", "name", "String");
            attribute(api, profile, "Signal", "values", "String");
            api.createAttribute("CartagoAgentIdentity", "globalId", "String");
            api.createAttribute("CartagoAgentIdentity", "localId", "Integer");
            api.createAttribute("CartagoAgentIdentity", "name", "String");
            api.createAttribute("CartagoAgentIdentity", "role", "String");
            api.createAttribute("CartagoAgentIdentity", "workspaceSemanticId", "String");
            attribute(api, profile, "AgentProgram", "declarationId", "String");
            attribute(api, profile, "AgentProgram", "sourceUri", "String");
            attribute(api, profile, "AgentProgram", "sourceDigest", "String");
            api.createAttribute("Plan", "ordinal", "Integer");
            api.createAttribute("Plan", "label", "String");
            // "context" is a USE grammar keyword. Keep source semantics exact while using a target-only safe name.
            api.createAttribute("Plan", "jasonContext", "String");
            attribute(api, profile, "Trigger", "operator", "TriggerOperator");
            attribute(api, profile, "Trigger", "triggerType", "TriggerType");
            attribute(api, profile, "Trigger", "literal", "String");
            api.createAttribute("PlanBodyElement", "ordinal", "Integer");
            api.createAttribute("PlanBodyElement", "bodyType", "PlanBodyType");
            api.createAttribute("PlanBodyElement", "term", "String");
            attribute(api, profile, "Action", "planBodySemanticId", "String");
            attribute(api, profile, "Action", "term", "String");
            attribute(api, profile, "Action", "functor", "String");
            attribute(api, profile, "Action", "arity", "Integer");
            attribute(api, profile, "Action", "kind", "ActionKind");
            attribute(api, profile, "Belief", "ordinal", "Integer");
            attribute(api, profile, "Belief", "literal", "String");
            attribute(api, profile, "AgentGoal", "ordinal", "Integer");
            attribute(api, profile, "AgentGoal", "literal", "String");
            attribute(api, profile, "AgentGoal", "goalKind", "String");
            attribute(api, profile, "BeliefRule", "ordinal", "Integer");
            attribute(api, profile, "BeliefRule", "head", "String");
            attribute(api, profile, "BeliefRule", "body", "String");
            if (profile.materializesClass("A17PlanOrderEntry"))
                api.createAttribute("A17PlanOrderEntry", "rank", "Integer");
            if (profile.materializesClass("A19BodyOrderEntry"))
                api.createAttribute("A19BodyOrderEntry", "rank", "Integer");
            api.createAttribute("Organization", "name", "String");
            api.createAttribute("Organization", "sourceUri", "String");
            api.createAttribute("StructuralSpecification", "organizationSemanticId", "String");
            api.createAttribute("StructuralSpecification", "specificationId", "String");
            api.createAttribute("StructuralSpecification", "rootGroupSemanticId", "String");
            api.createAttribute("FunctionalSpecification", "organizationSemanticId", "String");
            api.createAttribute("FunctionalSpecification", "specificationId", "String");
            api.createAttribute("NormativeSpecification", "organizationSemanticId", "String");
            api.createAttribute("NormativeSpecification", "specificationId", "String");
            api.createAttribute("Group", "groupId", "String");
            api.createAttribute("Group", "parentGroupSemanticId", "String");
            api.createAttribute("Role", "roleId", "String");
            api.createAttribute("Role", "isAbstract", "Boolean");
            api.createAttribute("RoleRelation", "relationId", "String");
            api.createAttribute("RoleRelation", "relationKind", "String");
            api.createAttribute("RoleRelation", "groupSemanticId", "String");
            api.createAttribute("RoleRelation", "sourceRoleSemanticId", "String");
            api.createAttribute("RoleRelation", "targetRoleSemanticId", "String");
            api.createAttribute("RoleRelation", "scope", "String");
            api.createAttribute("RoleRelation", "extendsToSubGroups", "Boolean");
            api.createAttribute("RoleRelation", "bidirectional", "Boolean");
            api.createAttribute("Link", "roleRelationSemanticId", "String");
            api.createAttribute("Link", "linkType", "String");
            api.createAttribute("Compatibility", "roleRelationSemanticId", "String");
            api.createAttribute("Scheme", "schemeId", "String");
            api.createAttribute("Scheme", "functionalSpecificationSemanticId", "String");
            api.createAttribute("Scheme", "rootGoalSemanticId", "String");
            api.createAttribute("Mission", "missionId", "String");
            api.createAttribute("Mission", "schemeSemanticId", "String");
            api.createAttribute("Mission", "goalSemanticIds", "String");
            api.createAttribute("OrganizationalGoal", "goalId", "String");
            api.createAttribute("OrganizationalGoal", "schemeSemanticId", "String");
            api.createAttribute("OrganizationalGoal", "goalType", "MoiseGoalType");
            api.createAttribute("OrganizationalGoal", "description", "String");
            api.createAttribute("OrganizationalGoal", "arguments", "String");
            api.createAttribute("OrganizationalGoal", "minAgentsToSatisfy", "Integer");
            api.createAttribute("OrganizationalGoal", "ttf", "String");
            api.createAttribute("OrganizationalGoal", "location", "String");
            api.createAttribute("OrganizationalGoal", "dependencySemanticIds", "String");
            api.createAttribute("OrganizationalGoal", "planSemanticId", "String");
            api.createAttribute("OrganizationalGoal", "inPlanSemanticId", "String");
            api.createAttribute("OrganizationalPlan", "planId", "String");
            api.createAttribute("OrganizationalPlan", "schemeSemanticId", "String");
            api.createAttribute("OrganizationalPlan", "targetGoalSemanticId", "String");
            api.createAttribute("OrganizationalPlan", "planOperator", "MoisePlanOperator");
            api.createAttribute("OrganizationalPlan", "successRate", "Real");
            api.createAttribute("Norm", "normId", "String");
            api.createAttribute("Norm", "normativeSpecificationSemanticId", "String");
            api.createAttribute("Norm", "roleSemanticId", "String");
            api.createAttribute("Norm", "missionSemanticId", "String");
            api.createAttribute("Norm", "normType", "MoiseNormType");
            api.createAttribute("Norm", "condition", "String");
            api.createAttribute("Norm", "timeConstraint", "String");
            api.createAttribute("GroupRoleCardinality", "groupSemanticId", "String");
            api.createAttribute("GroupRoleCardinality", "roleSemanticId", "String");
            api.createAttribute("GroupRoleCardinality", "minCardinality", "Integer");
            api.createAttribute("GroupRoleCardinality", "maxCardinality", "Integer");
            api.createAttribute("SubGroupCardinality", "parentGroupSemanticId", "String");
            api.createAttribute("SubGroupCardinality", "subGroupSemanticId", "String");
            api.createAttribute("SubGroupCardinality", "minCardinality", "Integer");
            api.createAttribute("SubGroupCardinality", "maxCardinality", "Integer");
            api.createAttribute("SchemeMissionCardinality", "schemeSemanticId", "String");
            api.createAttribute("SchemeMissionCardinality", "missionSemanticId", "String");
            api.createAttribute("SchemeMissionCardinality", "minCardinality", "Integer");
            api.createAttribute("SchemeMissionCardinality", "maxCardinality", "Integer");
            if (profile.materializesClass("ExactBindingEvidence")) {
                api.createAttribute("ExactBindingEvidence", "ruleId", "String");
                api.createAttribute("ExactBindingEvidence", "sourceIds", "String");
                api.createAttribute("ExactBindingEvidence", "targetIds", "String");
                // "context" is a USE grammar keyword; retain the exact evidence under a target-safe name.
                api.createAttribute("ExactBindingEvidence", "bindingContext", "String");
            }

            if (profile.materializesClass("AgentProgram"))
                association(api, "A16AgentProgramPlanLibrary", "AgentProgram", "agentProgram", "1",
                        MAggregationKind.COMPOSITION, "PlanLibrary", "planLibrary", "1", false, false);
            association(api, "A17PlanLibraryPlan", "PlanLibrary", "planLibrary", "1",
                    MAggregationKind.COMPOSITION, "Plan", "plans", "*", false, true);
            if (profile.materializesClass("Trigger"))
                association(api, "A18PlanTrigger", "Plan", "plan", "1",
                        MAggregationKind.COMPOSITION, "Trigger", "trigger", "1", false, false);
            association(api, "A19PlanBodyElement", "Plan", "plan", "1",
                    MAggregationKind.COMPOSITION, "PlanBodyElement", "bodyElements", "*", false, true);
            association(api, "A20PlanBodyNext", "PlanBodyElement", "current", "0..1",
                    MAggregationKind.NONE, "PlanBodyElement", "next", "0..1", false, false);
            if (profile.materializesClass("AgentProgram") && profile.materializesClass("Belief"))
                association(api, "A21ProgramBelief", "AgentProgram", "program", "1",
                        MAggregationKind.COMPOSITION, "Belief", "initialBeliefs", "*", false, true);
            if (profile.materializesClass("AgentProgram") && profile.materializesClass("AgentGoal"))
                association(api, "A22ProgramGoal", "AgentProgram", "program", "1",
                        MAggregationKind.COMPOSITION, "AgentGoal", "initialGoals", "*", false, true);
            if (profile.materializesOrderEntries()) {
                association(api, "A17OrderOwner", "PlanLibrary", "owner", "1", MAggregationKind.COMPOSITION,
                        "A17PlanOrderEntry", "a17Entries", "*", false, false);
                association(api, "A17OrderMember", "Plan", "member", "1", MAggregationKind.NONE,
                        "A17PlanOrderEntry", "a17Memberships", "0..1", false, false);
                association(api, "A19OrderOwner", "Plan", "owner", "1", MAggregationKind.COMPOSITION,
                        "A19BodyOrderEntry", "a19Entries", "*", false, false);
                association(api, "A19OrderMember", "PlanBodyElement", "member", "1", MAggregationKind.NONE,
                        "A19BodyOrderEntry", "a19Memberships", "0..1", false, false);
            }
            association(api, "C13EnvironmentWorkspace", "Environment", "environment", "1",
                    MAggregationKind.COMPOSITION, "Workspace", "workspaces", "*", false, true);
            association(api, "C14WorkspaceArtifact", "Workspace", "workspace", "1",
                    MAggregationKind.COMPOSITION, "Artifact", "artifacts", "*", false, true);
            association(api, "C15ArtifactType", "Artifact", "artifact", "*", MAggregationKind.NONE,
                    "ArtifactType", "artifacts", "1", false, false);
            if (profile.materializesClass("Operation"))
                association(api, "C16ArtifactOperation", "Artifact", "artifact", "1",
                        MAggregationKind.COMPOSITION, "Operation", "artifactOperations", "*", false, true);
            association(api, "C17ArtifactObservableProperty", "Artifact", "artifact", "1",
                    MAggregationKind.COMPOSITION, "ObservablePropertySnapshot", "properties", "*", false, true);
            if (profile.materializesClass("Operation") && profile.materializesClass("Guard"))
                association(api, "C18OperationGuard", "Operation", "operation", "1", MAggregationKind.NONE,
                        "Guard", "guard", "0..1", false, false);
            association(api, "C19WorkspaceAgent", "Workspace", "workspace", "1", MAggregationKind.NONE,
                    "CartagoAgentIdentity", "agents", "*", false, true);
            association(api, "C20AgentArtifactFocus", "CartagoAgentIdentity", "agent", "0..*", MAggregationKind.NONE,
                    "Artifact", "focusedArtifacts", "*", false, true);
            association(api, "M18OrganizationSS", "Organization", "m18StructuralSpecification", "1",
                    MAggregationKind.COMPOSITION, "StructuralSpecification", "m18Organization", "1", false, false);
            association(api, "M19OrganizationFS", "Organization", "m19FunctionalSpecification", "1",
                    MAggregationKind.COMPOSITION, "FunctionalSpecification", "m19Organization", "1", false, false);
            association(api, "M20OrganizationNS", "Organization", "m20NormativeSpecification", "1",
                    MAggregationKind.COMPOSITION, "NormativeSpecification", "m20Organization", "1", false, false);
            association(api, "M21SSRole", "StructuralSpecification", "m21Roles", "1",
                    MAggregationKind.COMPOSITION, "Role", "m21StructuralSpecification", "*", false, true);
            association(api, "M22SSGroup", "StructuralSpecification", "m22RootGroup", "0..1",
                    MAggregationKind.COMPOSITION, "Group", "m22StructuralSpecification", "1", false, false);
            association(api, "M23GroupSubgroup", "Group", "m23ParentGroup", "0..1",
                    MAggregationKind.COMPOSITION, "Group", "m23Subgroups", "*", false, true);
            association(api, "M24RoleSuperRole", "Role", "m24SubRole", "0..*", MAggregationKind.NONE,
                    "Role", "m24SuperRoles", "0..*", false, false);
            association(api, "M07RoleRelationLink", "RoleRelation", "m07LinkRelation", "1",
                    MAggregationKind.NONE, "Link", "m07RoleRelation", "0..1", false, false);
            association(api, "M07RoleRelationCompatibility", "RoleRelation", "m07CompatibilityRelation", "1",
                    MAggregationKind.NONE, "Compatibility", "m07CompatibilityRelation", "0..1", false, false);
            association(api, "M25LinkSource", "Link", "m25SourceLink", "0..*", MAggregationKind.NONE,
                    "Role", "m25SourceRole", "1", false, false);
            association(api, "M26LinkTarget", "Link", "m26TargetLink", "0..*", MAggregationKind.NONE,
                    "Role", "m26TargetRole", "1", false, false);
            association(api, "M27CompatibilitySource", "Compatibility", "m27SourceCompatibility", "0..*", MAggregationKind.NONE,
                    "Role", "m27SourceRole", "1", false, false);
            association(api, "M28CompatibilityTarget", "Compatibility", "m28TargetCompatibility", "0..*", MAggregationKind.NONE,
                    "Role", "m28TargetRole", "1", false, false);
            association(api, "M29CardinalityOwner", "GroupRoleCardinality", "m29GroupRoleCardinality", "0..*",
                    MAggregationKind.NONE, "Group", "m29RoleCardinalities", "1", false, false);
            association(api, "M30CardinalityMember", "GroupRoleCardinality", "m30GroupRoleCardinality", "0..*",
                    MAggregationKind.NONE, "Role", "m30RoleMemberships", "1", false, false);
            association(api, "M31SubgroupCardinalityOwner", "SubGroupCardinality", "m31ParentCardinality", "0..*",
                    MAggregationKind.NONE, "Group", "m31SubgroupCardinalities", "1", false, false);
            association(api, "M32SubgroupCardinalityMember", "SubGroupCardinality", "m32ChildCardinality", "0..*",
                    MAggregationKind.NONE, "Group", "m32ParentCardinalities", "1", false, false);
            association(api, "M33FSScheme", "FunctionalSpecification", "m33Schemes", "1",
                    MAggregationKind.COMPOSITION, "Scheme", "m33FunctionalSpecification", "*", false, true);
            association(api, "M34SchemeMission", "Scheme", "m34Missions", "1", MAggregationKind.COMPOSITION,
                    "Mission", "m34Scheme", "*", false, true);
            association(api, "M35SchemeRootGoal", "Scheme", "m35RootGoal", "0..1", MAggregationKind.NONE,
                    "OrganizationalGoal", "m35Scheme", "0..1", false, false);
            association(api, "M36SchemeCardinality", "SchemeMissionCardinality", "m36SchemeCardinality", "0..*",
                    MAggregationKind.NONE, "Scheme", "m36MissionCardinalities", "1", false, false);
            association(api, "M37MissionCardinality", "SchemeMissionCardinality", "m37MissionCardinality", "0..*",
                    MAggregationKind.NONE, "Mission", "m37SchemeCardinalities", "1", false, false);
            association(api, "M38MissionGoal", "Mission", "m38Goals", "0..*", MAggregationKind.NONE,
                    "OrganizationalGoal", "m38Missions", "0..*", false, true);
            association(api, "M39GoalPlan", "OrganizationalGoal", "m39Plan", "1", MAggregationKind.NONE,
                    "OrganizationalPlan", "m39TargetGoal", "0..1", false, false);
            association(api, "M40PlanSubGoals", "OrganizationalPlan", "m40Plan", "0..1", MAggregationKind.NONE,
                    "OrganizationalGoal", "m40SubGoals", "*", false, true);
            association(api, "M41NSNorm", "NormativeSpecification", "m41Norms", "1",
                    MAggregationKind.COMPOSITION, "Norm", "m41NormativeSpecification", "*", false, true);
            association(api, "M42NormRole", "Norm", "m42Role", "0..1", MAggregationKind.NONE,
                    "Role", "m42Norms", "0..*", false, true);
            association(api, "M43NormMission", "Norm", "m43Mission", "0..1", MAggregationKind.NONE,
                    "Mission", "m43Norms", "0..*", false, true);
            if (profile.materializesClass("Action") && profile.materializesClass("Operation"))
                association(api, "X01ActionOperation", "Action", "x01Operation", "0..1",
                        MAggregationKind.NONE, "Operation", "x01Actions", "0..*", false, true);
            // C08 live properties are unavailable; X02 binds to the exact C09 property snapshot.
            if (profile.materializesClass("Belief"))
                association(api, "X02BeliefProperty", "Belief", "x02Property", "0..1",
                        MAggregationKind.NONE, "ObservablePropertySnapshot", "x02Beliefs", "0..*", false, true);
            if (profile.materializesClass("Trigger") && profile.materializesClass("Signal"))
                association(api, "X03TriggerSignal", "Trigger", "x03Signal", "0..1",
                        MAggregationKind.NONE, "Signal", "x03Triggers", "0..*", false, true);
            association(api, "X04AgentRole", "Agent", "x04Roles", "0..*",
                    MAggregationKind.NONE, "Role", "x04Agents", "0..*", false, true);
            association(api, "X05AgentWorkspace", "Agent", "x05Workspaces", "0..*",
                    MAggregationKind.NONE, "Workspace", "x05Agents", "0..*", false, true);
            association(api, "X06AgentArtifactFocus", "Agent", "x06Artifacts", "0..*",
                    MAggregationKind.NONE, "Artifact", "x06Agents", "0..*", false, true);
            if (profile.materializesClass("AgentGoal"))
                association(api, "X07AgentGoalOrganizationalGoal", "AgentGoal", "x07OrganizationalGoal", "0..1",
                        MAggregationKind.NONE, "OrganizationalGoal", "x07AgentGoals", "0..*", false, true);
            if (profile.materializesClass("ArtifactDeclaration"))
                association(api, "X08DeclarationArtifact", "ArtifactDeclaration", "x08RuntimeArtifact", "0..1",
                        MAggregationKind.NONE, "Artifact", "x08Declarations", "0..*", false, true);
            association(api, "X09AgentIdentity", "Agent", "x09CartagoIdentities", "0..*",
                    MAggregationKind.NONE, "CartagoAgentIdentity", "x09Agents", "0..*", false, true);

            for (NativeOperationProjection projection : operationPlan.operations())
                api.createOperation(projection.ownerUseClass(), projection.descriptor().name(),
                        projection.parameters(), projection.returnType());

            List<NativeConstraintSpec> constraints = new CodeGroundedConstraintPlanner().plan(catalog, profile);
            NativeConstraintInstaller.InstallationResult installation =
                    new NativeConstraintInstaller().installWithReport(api, constraints);
            MModel model = api.getModel();
            modelTraces(trace, catalog, source, operationPlan, model, profile);
            return new Result(model, trace.index(), constraints, installation.skipped(),
                    operationPlan.artifactTypeClassNames(), operationPlan.operationDescriptorIds(),
                    NativeUseStructure.sha256(model), profile);
        } catch (UseApiException error) {
            throw new IllegalStateException("NATIVE_USE_MODEL_BUILD_FAILED: " + error.getMessage(), error);
        }
    }

    private static void modelTraces(CodeGroundedTraceCollector trace, CodeGroundedRuleCatalog catalog,
                                    JacamoSpecificationModel source, NativeOperationPlan operationPlan,
                                    MModel model, NativeProjectionProfile profile) {
        trace.add(catalog.require("J01"), TracePhase.MODEL_DECLARATION, source.project().metadata(),
                "MModel", "model:" + modelName(source.project().name()), List.of());
        for (String id : List.of("J02", "J03", "J04", "J05", "J06", "J07", "J08", "J09", "J10", "J11",
                "C01", "C02", "C03", "C04", "C05", "C06", "C07", "C08", "C09", "C10", "C11", "C12",
                "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20",
                "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11",
                "M12", "M13", "M14", "M15", "M16", "M17", "M18", "M19", "M20", "M21", "M22",
                "M23", "M24", "M25", "M26", "M27", "M28", "M29", "M30", "M31", "M32", "M33",
                "M34", "M35", "M36", "M37", "M38", "M39", "M40", "M41", "M42", "M43",
                "A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08", "A09", "A10",
                "A11", "A16", "A17", "A18", "A19", "A20", "A21", "A22",
                "X01", "X02", "X03", "X04", "X05", "X06", "X07", "X08", "X09")) {
            CodeGroundedRule rule = catalog.require(id);
            trace.add(new CodeGroundedTraceRecord(id, TracePhase.MODEL_DECLARATION, rule.sourceKindFqcn(),
                    rule.sourceKindFqcn(), "schema:" + id, targetKind(rule.targetUseKind()),
                    declarationIdentity(rule.targetUseKind()), rule.sourceAuthority(), rule.fidelity(),
                    rule.capabilityStatus(), List.of()));
        }
        for (NativeOperationProjection projection : operationPlan.operations())
            trace.add(catalog.require("C06"), TracePhase.MODEL_DECLARATION, projection.backing().metadata(),
                    "MOperation", projection.ownerUseClass() + "::" + projection.descriptor().name(),
                    List.of("EXACT_REFLECTION_SIGNATURE", "NATIVE_OPERATION_PROJECTED"));
        model.classes().stream().sorted(Comparator.comparing(value -> value.name())).forEach(cls -> {
            CodeGroundedRule rule = declarationRule(catalog, cls.name(), operationPlan);
            trace.add(declarationRecord(rule, "MClass", "class:" + cls.name(),
                    operationPlan.artifactTypeClassNames().containsValue(cls.name())
                            ? List.of("GENERATED_EXACT_ARTIFACT_SUBTYPE") : List.of()));
            cls.attributes().stream().sorted(Comparator.comparing(value -> value.name())).forEach(attribute ->
                    trace.add(declarationRecord(rule, "MAttribute",
                            "attribute:" + cls.name() + "." + attribute.name(), List.of())));
        });
        model.enumTypes().stream().sorted(Comparator.comparing(value -> value.name())).forEach(enumeration -> {
            CodeGroundedRule rule = catalog.require(enumRule(enumeration.name()));
            trace.add(declarationRecord(rule, "EnumType", "enum:" + enumeration.name(),
                    List.of("LITERALS=" + String.join(",", enumeration.getLiterals()))));
        });
        profile.conceptStatuses().forEach((concept, status) -> {
            if (status == NativeProjectionStatus.MATERIALIZED) return;
            CodeGroundedRule rule = projectionRule(catalog, concept);
            trace.add(new CodeGroundedTraceRecord(rule.ruleId(), TracePhase.MODEL_DECLARATION,
                    rule.sourceKindFqcn(), rule.sourceKindFqcn(), "projection:" + concept,
                    "ProjectionStatus", "projection:" + concept, rule.sourceAuthority(), rule.fidelity(),
                    rule.capabilityStatus(), List.of("STATUS=" + status.name(),
                            "RATIONALE=" + profile.rationales().get(concept))));
        });
    }

    private static CodeGroundedRule projectionRule(CodeGroundedRuleCatalog catalog, String concept) {
        return switch (concept) {
            case "ExactBindingEvidence" -> catalog.require("X01");
            case "A17PlanOrderEntry" -> catalog.require("A17");
            case "A19BodyOrderEntry" -> catalog.require("A19");
            case "BackingJavaOperation" -> catalog.require("C06");
            case "LiveObservableProperty" -> catalog.require("C08");
            case "ArtifactInfo" -> catalog.require("C10");
            case "WorkspaceDeclaration" -> catalog.require("J03");
            case "ArtifactDeclaration" -> catalog.require("J04");
            case "OrganizationDeployment" -> catalog.require("J05");
            case "GroupDeployment" -> catalog.require("J06");
            case "SchemeDeployment" -> catalog.require("J07");
            case "InstitutionDeployment" -> catalog.require("J08");
            case "AgentProgram" -> catalog.require("A01");
            case "Trigger" -> catalog.require("A04");
            case "Action" -> catalog.require("A06");
            case "Belief" -> catalog.require("A08");
            case "AgentGoal" -> catalog.require("A09");
            case "BeliefRule" -> catalog.require("A10");
            case "Signal" -> catalog.require("C11");
            case "Guard" -> catalog.require("C07");
            case "Operation" -> catalog.require("C05");
            case "SourceImportProvenance", "RuntimeEvidenceHistory", "SnapshotOnlyHelpers" -> catalog.require("J11");
            default -> throw new IllegalStateException("NATIVE_PROJECTION_RULE_MISSING:" + concept);
        };
    }

    private static CodeGroundedTraceRecord declarationRecord(CodeGroundedRule rule, String targetKind,
                                                               String targetIdentity, List<String> diagnostics) {
        return new CodeGroundedTraceRecord(rule.ruleId(), TracePhase.MODEL_DECLARATION, rule.sourceKindFqcn(),
                rule.sourceKindFqcn(), "schema:" + rule.ruleId(), targetKind, targetIdentity,
                rule.sourceAuthority(), rule.fidelity(), rule.capabilityStatus(), diagnostics);
    }

    private static CodeGroundedRule declarationRule(CodeGroundedRuleCatalog catalog, String className,
                                                      NativeOperationPlan operationPlan) {
        if (operationPlan.artifactTypeClassNames().containsValue(className)) return catalog.require("C03");
        String special = switch (className) {
            case "A17PlanOrderEntry" -> "A17";
            case "A19BodyOrderEntry" -> "A19";
            case "BackingJavaOperation" -> "C06";
            case "ExactBindingEvidence" -> "X01";
            default -> null;
        };
        if (special != null) return catalog.require(special);
        return catalog.rules().stream()
                .filter(rule -> rule.targetUseKind().equals("MClass " + className))
                .min(Comparator.comparing(CodeGroundedRule::ruleId))
                .orElseThrow(() -> new IllegalStateException("NATIVE_CLASS_TRACE_RULE_MISSING:" + className));
    }

    private static String enumRule(String enumName) {
        return switch (enumName) {
            case "TriggerOperator", "TriggerType" -> "A04";
            case "PlanBodyType" -> "A05";
            case "ActionKind" -> "A06";
            case "MoisePlanOperator" -> "M13";
            case "MoiseGoalType" -> "M12";
            case "MoiseNormType" -> "M14";
            default -> throw new IllegalStateException("NATIVE_ENUM_TRACE_RULE_MISSING:" + enumName);
        };
    }

    private static String targetKind(String target) {
        int space = target.indexOf(' ');
        return space < 0 ? target : target.substring(0, space);
    }

    private static String declarationIdentity(String target) {
        return target.replace(' ', ':');
    }

    private static void attribute(UseModelApi api, NativeProjectionProfile profile, String className,
                                  String attributeName, String type) throws UseApiException {
        if (profile.materializesClass(className)) api.createAttribute(className, attributeName, type);
    }

    private static void association(UseModelApi api, String name, String firstClass, String firstRole,
                                    String firstMultiplicity, int firstAggregation, String secondClass,
                                    String secondRole, String secondMultiplicity, boolean firstOrdered,
                                    boolean secondOrdered) throws UseApiException {
        api.createAssociation(name, new String[] {firstClass, secondClass}, new String[] {firstRole, secondRole},
                new String[] {firstMultiplicity, secondMultiplicity},
                new int[] {firstAggregation, MAggregationKind.NONE}, new boolean[] {firstOrdered, secondOrdered},
                new String[][][] {});
    }

    public static String modelName(String source) {
        String safe = source == null ? "JaCaMo" : source.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isBlank()) safe = "JaCaMo";
        if (Character.isDigit(safe.charAt(0))) safe = "JaCaMo_" + safe;
        return safe + "_CodeGrounded";
    }

    /** Canonical JSON is used as a scalar only where the USE schema has no typed collection projection. */
    public static String canonicalJson(Object value) {
        return new String(CanonicalJson.encode(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String nativeArtifactTypeClassName(String semanticId) {
        return "CArtAgOArtifactType_" + hash12(semanticId);
    }

    private static String hash12(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Class<?> loadClass(String name) {
        try {
            return switch (name) {
                case "boolean" -> boolean.class;
                case "byte" -> byte.class;
                case "short" -> short.class;
                case "int" -> int.class;
                case "long" -> long.class;
                case "float" -> float.class;
                case "double" -> double.class;
                case "char" -> char.class;
                case "void" -> void.class;
                default -> Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            };
        } catch (ClassNotFoundException | LinkageError error) {
            return null;
        }
    }

    private static String useType(String javaType) {
        return switch (javaType) {
            case "boolean", "java.lang.Boolean" -> "Boolean";
            case "byte", "java.lang.Byte", "short", "java.lang.Short", "int", "java.lang.Integer",
                    "long", "java.lang.Long" -> "Integer";
            case "float", "java.lang.Float", "double", "java.lang.Double" -> "Real";
            case "java.lang.String" -> "String";
            default -> null;
        };
    }

    private static boolean validUseIdentifier(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private record NativeOperationProjection(OperationDescriptorSemantic descriptor,
                                             BackingJavaOperationSemantic backing,
                                             String ownerUseClass, String[][] parameters,
                                             String returnType) { }

    private record NativeOperationPlan(Map<String, String> artifactTypeClassNames,
                                       List<NativeOperationProjection> operations) {
        private NativeOperationPlan {
            artifactTypeClassNames = Collections.unmodifiableMap(new LinkedHashMap<>(artifactTypeClassNames));
            operations = List.copyOf(operations);
        }

        private Set<String> operationDescriptorIds() {
            Set<String> ids = new LinkedHashSet<>();
            operations.forEach(value -> ids.add(value.descriptor().metadata().semanticId()));
            return Collections.unmodifiableSet(ids);
        }

        private static NativeOperationPlan resolve(JacamoSpecificationModel source) {
            Map<String, ArtifactTypeSemantic> types = new LinkedHashMap<>();
            Map<String, ArtifactSemantic> artifacts = new LinkedHashMap<>();
            Map<String, OperationDescriptorSemantic> descriptors = new LinkedHashMap<>();
            Map<String, BackingJavaOperationSemantic> backing = new LinkedHashMap<>();
            source.snapshot().cartagoEnvironments().stream()
                    .sorted(Comparator.comparing(value -> value.metadata().semanticId())).forEach(environment -> {
                        environment.artifactTypes().forEach(value -> types.put(value.metadata().semanticId(), value));
                        environment.artifacts().forEach(value -> artifacts.put(value.metadata().semanticId(), value));
                        environment.operations().forEach(value -> descriptors.put(value.metadata().semanticId(), value));
                        environment.backingOperations().forEach(value -> backing.put(value.metadata().semanticId(), value));
                    });

            Map<String, String> typeClassNames = new LinkedHashMap<>();
            List<NativeOperationProjection> projections = new ArrayList<>();
            backing.values().stream().sorted(Comparator.comparing(value -> value.metadata().semanticId())).forEach(value -> {
                OperationDescriptorSemantic descriptor = descriptors.get(value.operationDescriptorId());
                ArtifactSemantic artifact = descriptor == null ? null : artifacts.get(descriptor.artifactSemanticId());
                ArtifactTypeSemantic type = artifact == null ? null : types.get(artifact.artifactTypeSemanticId());
                NativeOperationProjection projection = exactProjection(descriptor, value, type);
                if (projection != null) {
                    typeClassNames.putIfAbsent(type.metadata().semanticId(), projection.ownerUseClass());
                    projections.add(projection);
                }
            });
            return new NativeOperationPlan(typeClassNames, projections);
        }

        private static NativeOperationProjection exactProjection(OperationDescriptorSemantic descriptor,
                                                                  BackingJavaOperationSemantic backing,
                                                                  ArtifactTypeSemantic type) {
            if (descriptor == null || type == null || descriptor.arity() < -1
                    || !validUseIdentifier(descriptor.name())) return null;
            Class<?> artifactClass = loadClass(type.javaClassName());
            Class<?> declaringClass = loadClass(backing.declaringClass());
            if (artifactClass == null || declaringClass == null || !declaringClass.isAssignableFrom(artifactClass)) return null;
            if (descriptor.arity() >= 0 && descriptor.arity() != backing.parameterTypes().size()) return null;
            Class<?>[] parameterTypes = new Class<?>[backing.parameterTypes().size()];
            String[][] parameters = new String[parameterTypes.length][2];
            for (int index = 0; index < parameterTypes.length; index++) {
                String javaType = backing.parameterTypes().get(index);
                parameterTypes[index] = loadClass(javaType);
                String useType = useType(javaType);
                if (parameterTypes[index] == null || useType == null) return null;
                parameters[index] = new String[] {"p" + index, useType};
            }
            Method method;
            try {
                method = declaringClass.getDeclaredMethod(backing.methodName(), parameterTypes);
            } catch (NoSuchMethodException error) {
                return null;
            }
            if (!Modifier.isPublic(method.getModifiers()) || !method.getReturnType().getName().equals(backing.returnType())
                    || method.isVarArgs() != backing.varArgs()) return null;
            String returnType = "void".equals(backing.returnType()) ? null : useType(backing.returnType());
            if (!"void".equals(backing.returnType()) && returnType == null) return null;
            return new NativeOperationProjection(descriptor, backing, nativeArtifactTypeClassName(type.metadata().semanticId()),
                    parameters, returnType);
        }
    }

    public record Result(MModel model, CodeGroundedTraceIndex trace, List<NativeConstraintSpec> constraints,
                         List<NativeConstraintSpec> skippedConstraints, Map<String, String> nativeArtifactTypeClassNames,
                         Set<String> nativeOperationDescriptorIds, String structuralHash,
                         NativeProjectionProfile profile) {
        public Result {
            constraints = List.copyOf(constraints);
            skippedConstraints = List.copyOf(skippedConstraints);
            nativeArtifactTypeClassNames = Collections.unmodifiableMap(new LinkedHashMap<>(nativeArtifactTypeClassNames));
            nativeOperationDescriptorIds = Collections.unmodifiableSet(new LinkedHashSet<>(nativeOperationDescriptorIds));
            profile = java.util.Objects.requireNonNull(profile, "profile");
        }
    }
}
