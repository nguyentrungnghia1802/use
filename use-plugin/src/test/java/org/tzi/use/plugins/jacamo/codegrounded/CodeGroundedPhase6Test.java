package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactTypeSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.CartagoAgentIdentitySemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.EnvironmentSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.FocusSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ObservablePropertySnapshotSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.SignalSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.WorkspaceSemantic;
import org.jacamo.bridge.contract.semantic.CrossSemanticContract;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;

/** Phase 6 proves exact cross-dimensional evidence without name-based joins. */
class CodeGroundedPhase6Test {
    @Test void bindingsProjectSupportedDomainRelationsAndKeepExecutionGoalEvidence() throws Exception {
        var base=withSyntheticJason(CodeGroundedTestFixtures.helloSnapshot()); var contract=base.semanticContract();
        var environment=environment(contract.project().metadata(),false); var bindings=allBindings(contract,environment);
        var result=new CodeGroundedNativePipeline().build(with(base,environment,bindings)); var system=result.state().system();
        for(var binding:bindings) assertTrue(result.trace().records().stream().anyMatch(r -> r.ruleId().equals(binding.ruleId())),binding.ruleId());
        assertEquals(1,links(system,org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("memberOf","Agent","Workspace")));
        String artifactClass=result.state().semanticObjectIndex().get(environment.artifacts().get(0).metadata().semanticId()).cls().name();
        assertEquals(1,links(system,org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("focuses","Agent",artifactClass)));
        var declaration=result.state().semanticObjectIndex().get(contract.agentDeclarations().get(0).metadata().semanticId());
        assertSame(declaration,result.state().semanticObjectIndex().get(environment.agents().get(0).metadata().semanticId()));
        for(String name:List.of("ExactBindingEvidence","CartagoAgentIdentity","Action","Operation","Role")) assertNull(system.model().getClass(name));
        assertNotNull(system.model().getClass("AgentGoal"));
        assertEquals(bindings,result.source().snapshot().exactBindings()); assertTrue(result.state().structureValid());
        assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
    }
    @Test void namesDoNotBindCartagoIdentityAndExplicitIncarnationDoesNotLeak() throws Exception {
        var base=withSyntheticJason(CodeGroundedTestFixtures.helloSnapshot()); var environment=environment(base.semanticContract().project().metadata(),true);
        var unbound=new CodeGroundedNativePipeline().build(with(base,environment,List.of()));
        assertNull(unbound.state().semanticObjectIndex().get(environment.agents().get(0).metadata().semanticId()));
        assertEquals(0,links(unbound.state().system(),org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("focuses","Agent","LiveRuntimePropertyArtifact")));
        assertFalse(unbound.state().system().state().allObjects().stream().anyMatch(o -> org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(o.cls()).equals("role")));
        var agentId=base.semanticContract().agentDeclarations().get(0).metadata().semanticId(); var identity=environment.agents().get(0).metadata().semanticId();
        var binding=new CrossSemanticContract.AgentIdentityBindingSemantic(bindingMetadata("phase6:x09:restart"),agentId,identity,"incarnation-1","join-observation",Map.of()).toExactBinding();
        var bound=new CodeGroundedNativePipeline().build(with(base,environment,List.of(binding)));
        assertSame(bound.state().semanticObjectIndex().get(agentId),bound.state().semanticObjectIndex().get(identity));
        assertNull(bound.state().semanticObjectIndex().get("phase6:cartago-agent:incarnation-2"));
    }
    @Test void wrongRetainedSourceEndpointFailsClosedEvenWithoutExecutionClasses() throws Exception {
        var base=withSyntheticJason(CodeGroundedTestFixtures.helloSnapshot()); var environment=environment(base.semanticContract().project().metadata(),false);
        var invalid=new CrossSemanticContract.ActionOperationBindingSemantic(bindingMetadata("phase6:x01:invalid"),
            base.semanticContract().jasonPrograms().get(0).actions().get(0).metadata().semanticId(),environment.artifacts().get(0).metadata().semanticId(),"dispatch-record",Map.of()).toExactBinding();
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new CodeGroundedNativePipeline().build(with(base,environment,List.of(invalid)))).getMessage().contains("CROSS_TARGET_CLASS_MISMATCH_X01"));
    }
    private static List<CrossSemanticContract.ExactBindingSemantic> allBindings(
            org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot contract, EnvironmentSemantic environment) {
        var program = contract.jasonPrograms().stream()
                .filter(value -> value.metadata().semanticId().equals("phase6:jason-program"))
                .findFirst().orElseThrow();
        var organization = contract.moiseOrganizations().get(0);
        var structural = organization.structuralSpecification();
        var functional = organization.functionalSpecification();
        var scheme = functional.schemes().get(0);
        return List.of(
                new CrossSemanticContract.ActionOperationBindingSemantic(bindingMetadata("phase6:x01"),
                        program.actions().get(0).metadata().semanticId(), environment.operations().get(0).metadata().semanticId(),
                        "dispatch:exact:phase6", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.BeliefObservablePropertyBindingSemantic(bindingMetadata("phase6:x02"),
                        program.beliefs().get(0).metadata().semanticId(),
                        environment.propertySnapshots().get(0).metadata().semanticId(),
                        "percept-property:sequence-7", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.TriggerSignalBindingSemantic(bindingMetadata("phase6:x03"),
                        program.planLibrary().plans().get(0).trigger().metadata().semanticId(),
                        environment.signals().get(0).metadata().semanticId(),
                        "signal-percept:sequence-8", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.AgentRoleBindingSemantic(bindingMetadata("phase6:x04"),
                        contract.agentDeclarations().get(0).metadata().semanticId(), structural.roles().get(0).metadata().semanticId(),
                        organization.metadata().semanticId(), structural.groups().get(0).groupId(),
                        "resolved-jcm-role-tuple", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.AgentWorkspaceBindingSemantic(bindingMetadata("phase6:x05"),
                        contract.agentDeclarations().get(0).metadata().semanticId(),
                        environment.workspaces().get(0).metadata().semanticId(),
                        "runtime-membership", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.AgentArtifactFocusBindingSemantic(bindingMetadata("phase6:x06"),
                        contract.agentDeclarations().get(0).metadata().semanticId(),
                        environment.artifacts().get(0).metadata().semanticId(),
                        "focus-event:sequence-9", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.AgentGoalOrganizationalGoalBindingSemantic(bindingMetadata("phase6:x07"),
                        program.goals().get(0).metadata().semanticId(), scheme.goals().get(0).metadata().semanticId(),
                        "explicit-goal-event", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.ArtifactDeclarationBindingSemantic(bindingMetadata("phase6:x08"),
                        contract.artifactDeclarations().get(0).metadata().semanticId(),
                        environment.artifacts().get(0).metadata().semanticId(),
                        "workspace.makeArtifact:correlation-10", Map.of("contextId", "phase6-all")).toExactBinding(),
                new CrossSemanticContract.AgentIdentityBindingSemantic(bindingMetadata("phase6:x09"),
                        contract.agentDeclarations().get(0).metadata().semanticId(),
                        environment.agents().get(0).metadata().semanticId(),
                        "incarnation-1", "join-observation", Map.of("contextId", "phase6-all")).toExactBinding());
    }

    private static String association(String ruleId) {
        return switch (ruleId) {
            case "X01" -> "X01ActionOperation";
            case "X02" -> "X02BeliefProperty";
            case "X03" -> "X03TriggerSignal";
            case "X04" -> "X04AgentRole";
            case "X05" -> "X05AgentWorkspace";
            case "X06" -> "X06AgentArtifactFocus";
            case "X07" -> "X07AgentGoalOrganizationalGoal";
            case "X08" -> "X08DeclarationArtifact";
            case "X09" -> "X09AgentIdentity";
            default -> throw new IllegalArgumentException(ruleId);
        };
    }

    private static int objects(org.tzi.use.uml.sys.MSystem system, String className) {
        return system.state().objectsOfClass(system.model().getClass(className)).size();
    }

    private static int links(org.tzi.use.uml.sys.MSystem system, String association) {
        return system.state().linksOfAssociation(system.model().getAssociation(association)).size();
    }

    private static ModelSnapshot with(ModelSnapshot base, EnvironmentSemantic environment,
                                      List<CrossSemanticContract.ExactBindingSemantic> bindings) {
        var contract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                contract.institutionDeployments(), contract.rawRoleTuples(), contract.rawFocusTuples(),
                contract.importProvenance(), contract.jasonPrograms(), List.of(environment),
                contract.moiseOrganizations(), bindings, contract.diagnostics());
        return new ModelSnapshot("phase6-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }

    private static ModelSnapshot withSyntheticJason(ModelSnapshot base) {
        var contract = base.semanticContract();
        String programId = "phase6:jason-program";
        String libraryId = "phase6:jason-library";
        String planId = "phase6:jason-plan";
        String triggerId = "phase6:jason-trigger";
        String bodyId = "phase6:jason-body";
        var body = new org.jacamo.bridge.contract.semantic.JasonSemanticContract.PlanBodyElementSemantic(
                jasonMeta(bodyId, "JASON_PLAN_BODY"), 0, "action", "same-name", "");
        var trigger = new org.jacamo.bridge.contract.semantic.JasonSemanticContract.TriggerSemantic(
                jasonMeta(triggerId, "JASON_TRIGGER"), "add", "belief", "same-name");
        var plan = new org.jacamo.bridge.contract.semantic.JasonSemanticContract.PlanSemantic(
                jasonMeta(planId, "JASON_PLAN"), 0, "same-name", "true", trigger, List.of(body));
        var program = new org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic(
                jasonMeta(programId, "JASON_AGENT_PROGRAM"), "phase6-agent", "phase6://agent.asl", "d".repeat(64),
                new org.jacamo.bridge.contract.semantic.JasonSemanticContract.PlanLibrarySemantic(
                        jasonMeta(libraryId, "JASON_PLAN_LIBRARY"), List.of(plan)),
                List.of(new org.jacamo.bridge.contract.semantic.JasonSemanticContract.ActionSemantic(
                        jasonMeta("phase6:jason-action", "JASON_EXTERNAL_ACTION"), bodyId, "same-name", "same-name", 1,
                        "EXTERNAL")),
                List.of(new org.jacamo.bridge.contract.semantic.JasonSemanticContract.BeliefSemantic(
                        jasonMeta("phase6:jason-belief", "JASON_BELIEF"), 0, "same-name")),
                List.of(new org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentGoalSemantic(
                        jasonMeta("phase6:jason-goal", "JASON_AGENT_GOAL"), 0, "same-name", "achievement")),
                List.of());
        var programs = new java.util.ArrayList<>(contract.jasonPrograms());
        programs.add(program);
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                contract.institutionDeployments(), contract.rawRoleTuples(), contract.rawFocusTuples(),
                contract.importProvenance(), programs, contract.cartagoEnvironments(), contract.moiseOrganizations(),
                contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("phase6-jason-fixture", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }

    private static EnvironmentSemantic environment(SemanticMetadata source, boolean restartedIdentity) {
        String env = "phase6:environment";
        String workspace = "phase6:workspace";
        String type = "phase6:artifact-type";
        String artifact = "phase6:artifact";
        String operation = "phase6:operation";
        String property = "phase6:property";
        String signal = "phase6:signal";
        String identity = "phase6:cartago-agent:incarnation-1";
        var workspaceValue = new WorkspaceSemantic(meta(workspace, "CARTAGO_WORKSPACE"), "/phase6", "same-name",
                "workspace-1", "", env, true, "local", "", "");
        var artifactValue = new ArtifactSemantic(meta(artifact, "CARTAGO_ARTIFACT"), "same-name", "artifact-1",
                type, workspace, identity);
        var agentValue = new CartagoAgentIdentitySemantic(meta(identity, "CARTAGO_AGENT_IDENTITY"),
                "same-name", 1, "same-name", "worker", workspace);
        var agents = restartedIdentity
                ? List.of(agentValue, new CartagoAgentIdentitySemantic(meta("phase6:cartago-agent:incarnation-2",
                        "CARTAGO_AGENT_IDENTITY"), "same-name", 2, "same-name", "worker", workspace))
                : List.of(agentValue);
        return new EnvironmentSemantic(meta(env, "CARTAGO_ENVIRONMENT"), "same-name", "environment-1", "1.0",
                "default", List.of(workspaceValue),
                List.of(new ArtifactTypeSemantic(meta(type, "CARTAGO_ARTIFACT_TYPE"), LiveRuntimePropertyArtifact.class.getName(), "loader")),
                List.of(artifactValue),
                List.of(new OperationDescriptorSemantic(meta(operation, "CARTAGO_OPERATION_DESCRIPTOR"), artifact,
                        "same-name-key", "same-name", 1, false, false, false, false)),
                List.of(), List.of(), List.of(),
                List.of(new ObservablePropertySnapshotSemantic(meta(property, "CARTAGO_OBSERVABLE_PROPERTY_SNAPSHOT"),
                        artifact, "same-name#0", "same-name", List.of("READY"), List.of("java.lang.String"), List.of())),
                List.of(), List.of(new SignalSemantic(meta(signal, "CARTAGO_SIGNAL"), artifact, "same-name", List.of("READY"))),
                agents, List.of(new FocusSemantic(meta("phase6:focus", "CARTAGO_FOCUS"), identity, artifact, true, 0)));
    }

    private static SemanticMetadata bindingMetadata(String id) {
        return new SemanticMetadata(id, "EXACT_CROSS_BINDING", CodeGroundedPhase6Test.class.getName(),
                EvidenceAuthority.EXPLICIT_BINDING, Fidelity.CONDITIONAL, CapabilityStatus.COMPLETE, List.of(), List.of());
    }

    private static SemanticMetadata jasonMeta(String id, String kind) {
        return new SemanticMetadata(id, kind, CodeGroundedPhase6Test.class.getName(),
                EvidenceAuthority.OFFICIAL_JASON_API, Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of());
    }

    private static SemanticMetadata meta(String id, String kind) {
        return new SemanticMetadata(id, kind, CodeGroundedPhase6Test.class.getName(),
                EvidenceAuthority.OFFICIAL_CARTAGO_API, Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of());
    }
}
