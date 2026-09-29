package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.List;
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

    public Result build(JacamoSpecificationModel source) {
        CodeGroundedTraceCollector trace = new CodeGroundedTraceCollector();
        return build(source, trace);
    }

    public Result build(JacamoSpecificationModel source, CodeGroundedTraceCollector trace) {
        var catalog = new CodeGroundedRuleCatalog();
        UseModelApi api = new UseModelApi(modelName(source.project().name()));
        try {
            api.createEnumeration("TriggerOperator", TRIGGER_OPERATORS);
            api.createEnumeration("TriggerType", TRIGGER_TYPES);
            api.createEnumeration("PlanBodyType", BODY_TYPES);
            api.createEnumeration("ActionKind", ACTION_KINDS);
            api.createEnumeration("MoisePlanOperator", MOISE_PLAN_OPERATORS);
            api.createEnumeration("MoiseGoalType", MOISE_GOAL_TYPES);
            api.createEnumeration("MoiseNormType", MOISE_NORM_TYPES);

            for (String name : List.of("Agent", "WorkspaceDeclaration", "ArtifactDeclaration", "OrganizationDeployment",
                    "GroupDeployment", "SchemeDeployment", "InstitutionDeployment", "AgentProgram", "PlanLibrary",
                    "Plan", "Trigger", "PlanBodyElement", "Action", "Belief", "AgentGoal", "BeliefRule",
                    "Environment", "Workspace", "ArtifactType", "Artifact", "Operation", "BackingJavaOperation",
                    "Guard", "LiveObservableProperty", "ObservablePropertySnapshot", "ArtifactInfo", "Signal",
                    "CartagoAgentIdentity", "A17PlanOrderEntry", "A19BodyOrderEntry", "Organization",
                    "StructuralSpecification", "FunctionalSpecification", "NormativeSpecification", "Group", "Role",
                    "RoleRelation", "Link", "Compatibility", "Scheme", "Mission", "OrganizationalGoal",
                    "OrganizationalPlan", "Norm", "GroupRoleCardinality", "SubGroupCardinality",
                    "SchemeMissionCardinality", "ExactBindingEvidence"))
                api.createClass(name, false);
            for (String name : List.of("Agent", "WorkspaceDeclaration", "ArtifactDeclaration", "OrganizationDeployment",
                    "GroupDeployment", "SchemeDeployment", "InstitutionDeployment", "AgentProgram", "PlanLibrary",
                    "Plan", "Trigger", "PlanBodyElement", "Action", "Belief", "AgentGoal", "BeliefRule",
                    "Environment", "Workspace", "ArtifactType", "Artifact", "Operation", "BackingJavaOperation",
                    "Guard", "LiveObservableProperty", "ObservablePropertySnapshot", "ArtifactInfo", "Signal",
                    "CartagoAgentIdentity", "A17PlanOrderEntry", "A19BodyOrderEntry", "Organization",
                    "StructuralSpecification", "FunctionalSpecification", "NormativeSpecification", "Group", "Role",
                    "RoleRelation", "Link", "Compatibility", "Scheme", "Mission", "OrganizationalGoal",
                    "OrganizationalPlan", "Norm", "GroupRoleCardinality", "SubGroupCardinality",
                    "SchemeMissionCardinality", "ExactBindingEvidence"))
                api.createAttribute(name, "semanticId", "String");
            api.createAttribute("Agent", "name", "String");
            api.createAttribute("Agent", "sourceUri", "String");
            api.createAttribute("Agent", "options", "String");
            api.createAttribute("Agent", "architectureClasses", "String");
            api.createAttribute("Agent", "agentClass", "String");
            api.createAttribute("Agent", "beliefBaseClass", "String");
            api.createAttribute("Agent", "host", "String");
            api.createAttribute("Agent", "instances", "Integer");
            api.createAttribute("WorkspaceDeclaration", "name", "String");
            api.createAttribute("WorkspaceDeclaration", "host", "String");
            api.createAttribute("WorkspaceDeclaration", "debug", "Boolean");
            api.createAttribute("ArtifactDeclaration", "name", "String");
            api.createAttribute("ArtifactDeclaration", "workspace", "String");
            api.createAttribute("ArtifactDeclaration", "javaClass", "String");
            api.createAttribute("ArtifactDeclaration", "parameters", "String");
            api.createAttribute("OrganizationDeployment", "name", "String");
            api.createAttribute("OrganizationDeployment", "source", "String");
            api.createAttribute("OrganizationDeployment", "institution", "String");
            api.createAttribute("OrganizationDeployment", "debug", "String");
            api.createAttribute("GroupDeployment", "organization", "String");
            api.createAttribute("GroupDeployment", "name", "String");
            api.createAttribute("GroupDeployment", "type", "String");
            api.createAttribute("GroupDeployment", "responsibleFor", "String");
            api.createAttribute("SchemeDeployment", "organization", "String");
            api.createAttribute("SchemeDeployment", "name", "String");
            api.createAttribute("SchemeDeployment", "type", "String");
            api.createAttribute("InstitutionDeployment", "name", "String");
            api.createAttribute("InstitutionDeployment", "workspaces", "String");
            api.createAttribute("InstitutionDeployment", "opaqueParameters", "String");
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
            api.createAttribute("Operation", "artifactSemanticId", "String");
            api.createAttribute("Operation", "keyId", "String");
            api.createAttribute("Operation", "name", "String");
            api.createAttribute("Operation", "arity", "Integer");
            api.createAttribute("Operation", "dynamic", "Boolean");
            api.createAttribute("Operation", "linkOperation", "Boolean");
            api.createAttribute("Operation", "ui", "Boolean");
            api.createAttribute("Operation", "internal", "Boolean");
            api.createAttribute("BackingJavaOperation", "operationDescriptorId", "String");
            api.createAttribute("BackingJavaOperation", "declaringClass", "String");
            api.createAttribute("BackingJavaOperation", "methodName", "String");
            api.createAttribute("BackingJavaOperation", "parameterTypes", "String");
            api.createAttribute("BackingJavaOperation", "returnType", "String");
            api.createAttribute("BackingJavaOperation", "varArgs", "Boolean");
            api.createAttribute("BackingJavaOperation", "classLoaderIdentity", "String");
            api.createAttribute("Guard", "operationDescriptorId", "String");
            api.createAttribute("Guard", "name", "String");
            api.createAttribute("Guard", "arity", "Integer");
            api.createAttribute("Guard", "implementationClass", "String");
            api.createAttribute("LiveObservableProperty", "artifactSemanticId", "String");
            api.createAttribute("LiveObservableProperty", "propertyId", "String");
            api.createAttribute("LiveObservableProperty", "name", "String");
            api.createAttribute("LiveObservableProperty", "values", "String");
            api.createAttribute("LiveObservableProperty", "annotations", "String");
            api.createAttribute("ObservablePropertySnapshot", "artifactSemanticId", "String");
            api.createAttribute("ObservablePropertySnapshot", "propertyId", "String");
            api.createAttribute("ObservablePropertySnapshot", "name", "String");
            api.createAttribute("ObservablePropertySnapshot", "values", "String");
            api.createAttribute("ObservablePropertySnapshot", "valueTypes", "String");
            api.createAttribute("ObservablePropertySnapshot", "annotations", "String");
            api.createAttribute("ArtifactInfo", "artifactSemanticId", "String");
            api.createAttribute("ArtifactInfo", "creatorAgentSemanticId", "String");
            api.createAttribute("ArtifactInfo", "operationSemanticIds", "String");
            api.createAttribute("ArtifactInfo", "observablePropertySemanticIds", "String");
            api.createAttribute("ArtifactInfo", "linkedArtifactSemanticIds", "String");
            api.createAttribute("Signal", "artifactSemanticId", "String");
            api.createAttribute("Signal", "name", "String");
            api.createAttribute("Signal", "values", "String");
            api.createAttribute("CartagoAgentIdentity", "globalId", "String");
            api.createAttribute("CartagoAgentIdentity", "localId", "Integer");
            api.createAttribute("CartagoAgentIdentity", "name", "String");
            api.createAttribute("CartagoAgentIdentity", "role", "String");
            api.createAttribute("CartagoAgentIdentity", "workspaceSemanticId", "String");
            api.createAttribute("AgentProgram", "declarationId", "String");
            api.createAttribute("AgentProgram", "sourceUri", "String");
            api.createAttribute("AgentProgram", "sourceDigest", "String");
            api.createAttribute("Plan", "ordinal", "Integer");
            api.createAttribute("Plan", "label", "String");
            // "context" is a USE grammar keyword. Keep source semantics exact while using a target-only safe name.
            api.createAttribute("Plan", "jasonContext", "String");
            api.createAttribute("Trigger", "operator", "TriggerOperator");
            api.createAttribute("Trigger", "triggerType", "TriggerType");
            api.createAttribute("Trigger", "literal", "String");
            api.createAttribute("PlanBodyElement", "ordinal", "Integer");
            api.createAttribute("PlanBodyElement", "bodyType", "PlanBodyType");
            api.createAttribute("PlanBodyElement", "term", "String");
            api.createAttribute("Action", "planBodySemanticId", "String");
            api.createAttribute("Action", "term", "String");
            api.createAttribute("Action", "functor", "String");
            api.createAttribute("Action", "arity", "Integer");
            api.createAttribute("Action", "kind", "ActionKind");
            api.createAttribute("Belief", "ordinal", "Integer");
            api.createAttribute("Belief", "literal", "String");
            api.createAttribute("AgentGoal", "ordinal", "Integer");
            api.createAttribute("AgentGoal", "literal", "String");
            api.createAttribute("AgentGoal", "goalKind", "String");
            api.createAttribute("BeliefRule", "ordinal", "Integer");
            api.createAttribute("BeliefRule", "head", "String");
            api.createAttribute("BeliefRule", "body", "String");
            api.createAttribute("A17PlanOrderEntry", "rank", "Integer");
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
            api.createAttribute("ExactBindingEvidence", "ruleId", "String");
            api.createAttribute("ExactBindingEvidence", "sourceIds", "String");
            api.createAttribute("ExactBindingEvidence", "targetIds", "String");
            // "context" is a USE grammar keyword; retain the exact evidence under a target-safe name.
            api.createAttribute("ExactBindingEvidence", "bindingContext", "String");

            association(api, "A16AgentProgramPlanLibrary", "AgentProgram", "agentProgram", "1",
                    MAggregationKind.COMPOSITION, "PlanLibrary", "planLibrary", "1", false, false);
            association(api, "A17PlanLibraryPlan", "PlanLibrary", "planLibrary", "1",
                    MAggregationKind.COMPOSITION, "Plan", "plans", "*", false, true);
            association(api, "A18PlanTrigger", "Plan", "plan", "1",
                    MAggregationKind.COMPOSITION, "Trigger", "trigger", "1", false, false);
            association(api, "A19PlanBodyElement", "Plan", "plan", "1",
                    MAggregationKind.COMPOSITION, "PlanBodyElement", "bodyElements", "*", false, true);
            association(api, "A20PlanBodyNext", "PlanBodyElement", "current", "0..1",
                    MAggregationKind.NONE, "PlanBodyElement", "next", "0..1", false, false);
            association(api, "A21ProgramBelief", "AgentProgram", "program", "1",
                    MAggregationKind.COMPOSITION, "Belief", "initialBeliefs", "*", false, true);
            association(api, "A22ProgramGoal", "AgentProgram", "program", "1",
                    MAggregationKind.COMPOSITION, "AgentGoal", "initialGoals", "*", false, true);
            association(api, "A17OrderOwner", "PlanLibrary", "owner", "1", MAggregationKind.COMPOSITION,
                    "A17PlanOrderEntry", "a17Entries", "*", false, false);
            association(api, "A17OrderMember", "Plan", "member", "1", MAggregationKind.NONE,
                    "A17PlanOrderEntry", "a17Memberships", "0..1", false, false);
            association(api, "A19OrderOwner", "Plan", "owner", "1", MAggregationKind.COMPOSITION,
                    "A19BodyOrderEntry", "a19Entries", "*", false, false);
            association(api, "A19OrderMember", "PlanBodyElement", "member", "1", MAggregationKind.NONE,
                    "A19BodyOrderEntry", "a19Memberships", "0..1", false, false);
            association(api, "C13EnvironmentWorkspace", "Environment", "environment", "1",
                    MAggregationKind.COMPOSITION, "Workspace", "workspaces", "*", false, true);
            association(api, "C14WorkspaceArtifact", "Workspace", "workspace", "1",
                    MAggregationKind.COMPOSITION, "Artifact", "artifacts", "*", false, true);
            association(api, "C15ArtifactType", "Artifact", "artifact", "*", MAggregationKind.NONE,
                    "ArtifactType", "artifacts", "1", false, false);
            association(api, "C16ArtifactOperation", "Artifact", "artifact", "1",
                    MAggregationKind.COMPOSITION, "Operation", "artifactOperations", "*", false, true);
            association(api, "C17ArtifactObservableProperty", "Artifact", "artifact", "1",
                    MAggregationKind.COMPOSITION, "ObservablePropertySnapshot", "properties", "*", false, true);
            association(api, "C18OperationGuard", "Operation", "operation", "1", MAggregationKind.NONE,
                    "Guard", "guard", "0..1", false, false);
            association(api, "C19WorkspaceAgent", "Workspace", "workspace", "1", MAggregationKind.NONE,
                    "CartagoAgentIdentity", "agents", "*", false, true);
            association(api, "C20AgentArtifactFocus", "CartagoAgentIdentity", "agent", "1", MAggregationKind.NONE,
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
            association(api, "X01ActionOperation", "Action", "x01Operation", "0..1",
                    MAggregationKind.NONE, "Operation", "x01Actions", "0..*", false, true);
            // C08 live properties are unavailable; X02 binds to the exact C09 property snapshot.
            association(api, "X02BeliefProperty", "Belief", "x02Property", "0..1",
                    MAggregationKind.NONE, "ObservablePropertySnapshot", "x02Beliefs", "0..*", false, true);
            association(api, "X03TriggerSignal", "Trigger", "x03Signal", "0..1",
                    MAggregationKind.NONE, "Signal", "x03Triggers", "0..*", false, true);
            association(api, "X04AgentRole", "Agent", "x04Roles", "0..*",
                    MAggregationKind.NONE, "Role", "x04Agents", "0..*", false, true);
            association(api, "X05AgentWorkspace", "Agent", "x05Workspaces", "0..*",
                    MAggregationKind.NONE, "Workspace", "x05Agents", "0..*", false, true);
            association(api, "X06AgentArtifactFocus", "Agent", "x06Artifacts", "0..*",
                    MAggregationKind.NONE, "Artifact", "x06Agents", "0..*", false, true);
            association(api, "X07AgentGoalOrganizationalGoal", "AgentGoal", "x07OrganizationalGoal", "0..1",
                    MAggregationKind.NONE, "OrganizationalGoal", "x07AgentGoals", "0..*", false, true);
            association(api, "X08DeclarationArtifact", "ArtifactDeclaration", "x08RuntimeArtifact", "0..1",
                    MAggregationKind.NONE, "Artifact", "x08Declarations", "0..*", false, true);
            association(api, "X09AgentIdentity", "Agent", "x09CartagoIdentities", "0..*",
                    MAggregationKind.NONE, "CartagoAgentIdentity", "x09Agents", "0..*", false, true);

            List<NativeConstraintSpec> constraints = new CodeGroundedConstraintPlanner().plan(catalog);
            NativeConstraintInstaller.InstallationResult installation =
                    new NativeConstraintInstaller().installWithReport(api, constraints);
            modelTraces(trace, catalog, source);
            MModel model = api.getModel();
            return new Result(model, trace.index(), constraints, installation.skipped(), NativeUseStructure.sha256(model));
        } catch (UseApiException error) {
            throw new IllegalStateException("NATIVE_USE_MODEL_BUILD_FAILED: " + error.getMessage(), error);
        }
    }

    private static void modelTraces(CodeGroundedTraceCollector trace, CodeGroundedRuleCatalog catalog,
                                    JacamoSpecificationModel source) {
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
    }

    private static String targetKind(String target) {
        int space = target.indexOf(' ');
        return space < 0 ? target : target.substring(0, space);
    }

    private static String declarationIdentity(String target) {
        return target.replace(' ', ':');
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

    public record Result(MModel model, CodeGroundedTraceIndex trace, List<NativeConstraintSpec> constraints,
                         List<NativeConstraintSpec> skippedConstraints, String structuralHash) {
        public Result {
            constraints = List.copyOf(constraints);
            skippedConstraints = List.copyOf(skippedConstraints);
        }
    }
}
