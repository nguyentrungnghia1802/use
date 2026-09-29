package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactInfoSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactTypeSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.BackingJavaOperationSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.CartagoAgentIdentitySemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.EnvironmentSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.FocusSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.GuardSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ObservablePropertySnapshotSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.SignalSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.WorkspaceSemantic;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.junit.jupiter.api.Test;

class CodeGroundedPhase4Test {
    @Test
    void nativePhase4MaterializesTypedCartagoSnapshotWithExactLinksAndUnavailableC08() throws Exception {
        var base = CodeGroundedTestFixtures.helloSnapshot();
        var environment = syntheticEnvironment(base.semanticContract().project().metadata());
        var result = new CodeGroundedNativePipeline().build(withEnvironment(base, environment));
        var system = result.state().system();

        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Environment")).size());
        assertEquals(2, system.state().objectsOfClass(system.model().getClass("Workspace")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("ArtifactType")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Artifact")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Operation")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("BackingJavaOperation")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Guard")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("ObservablePropertySnapshot")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("ArtifactInfo")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Signal")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("CartagoAgentIdentity")).size());
        assertEquals(0, system.state().objectsOfClass(system.model().getClass("LiveObservableProperty")).size());

        assertEquals(2, links(system, "C13EnvironmentWorkspace"));
        assertEquals(1, links(system, "C14WorkspaceArtifact"));
        assertEquals(1, links(system, "C15ArtifactType"));
        assertEquals(1, links(system, "C16ArtifactOperation"));
        assertEquals(1, links(system, "C17ArtifactObservableProperty"));
        assertEquals(1, links(system, "C18OperationGuard"));
        assertEquals(1, links(system, "C19WorkspaceAgent"));
        assertEquals(1, links(system, "C20AgentArtifactFocus"));

        String identityQuery = "Artifact.allInstances->exists(a | a.semanticId = 'phase4:artifact' and "
                + "a.uuid = 'artifact-uuid' and a.workspaceSemanticId = 'phase4:workspace:root')";
        assertEquals("true", org.tzi.use.api.UseSystemApi.create(system, false).evaluate(identityQuery).toString());
        assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals("C08")
                && value.targetKind().equals("UNAVAILABLE")
                && value.diagnostics().contains("C08_UNAVAILABLE_NO_LIVE_OBSPROPERTY_API")));
        assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals("C20")
                && value.targetKind().equals("FOCUS_EVENT")
                && value.diagnostics().contains("UNFOCUS")));
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
        assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
    }

    @Test
    void nonEmptyLivePropertyContractFailsClosedInsteadOfBecomingAStaticSnapshot() throws Exception {
        var base = CodeGroundedTestFixtures.helloSnapshot();
        var environment = syntheticEnvironment(base.semanticContract().project().metadata());
        var live = new org.jacamo.bridge.contract.semantic.CartagoSemanticContract.LiveObservablePropertySemantic(
                metadata("phase4:live", "CARTAGO_LIVE_OBSERVABLE_PROPERTY"), "phase4:artifact", "live", "live",
                List.of("value"), List.of());
        var invalid = new EnvironmentSemantic(environment.metadata(), environment.name(), environment.environmentId(),
                environment.version(), environment.defaultInfrastructureLayer(), environment.workspaces(),
                environment.artifactTypes(), environment.artifacts(), environment.operations(), environment.backingOperations(),
                environment.guards(), List.of(live), environment.propertySnapshots(), environment.artifactInfos(),
                environment.signals(), environment.agents(), environment.focuses());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new CodeGroundedNativePipeline().build(withEnvironment(base, invalid)));
        assertTrue(failure.getMessage().contains("C08_LIVE_PROPERTY_NOT_EXPOSED_BY_AUDITED_API"));
    }

    @Test
    void artifactTypeAndOperationReferencesRequireExactSemanticIds() throws Exception {
        var base = CodeGroundedTestFixtures.helloSnapshot();
        var environment = syntheticEnvironment(base.semanticContract().project().metadata());
        var mismatchedArtifact = new ArtifactSemantic(metadata("phase4:bad-artifact", "CARTAGO_ARTIFACT"),
                "artifact", "artifact-uuid", "example.Artifact", "phase4:workspace:root", "phase4:agent");
        var invalid = new EnvironmentSemantic(environment.metadata(), environment.name(), environment.environmentId(),
                environment.version(), environment.defaultInfrastructureLayer(), environment.workspaces(),
                environment.artifactTypes(), List.of(mismatchedArtifact), environment.operations(),
                environment.backingOperations(), environment.guards(), environment.liveProperties(),
                environment.propertySnapshots(), environment.artifactInfos(), environment.signals(), environment.agents(),
                environment.focuses());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new CodeGroundedNativePipeline().build(withEnvironment(base, invalid)));
        assertTrue(failure.getMessage().contains("C15_TYPE_REFERENCE"));
    }

    private static int links(org.tzi.use.uml.sys.MSystem system, String name) {
        return system.state().linksOfAssociation(system.model().getAssociation(name)).size();
    }

    private static ModelSnapshot withEnvironment(ModelSnapshot base, EnvironmentSemantic environment) {
        var contract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                contract.institutionDeployments(), contract.rawRoleTuples(), contract.rawFocusTuples(),
                contract.importProvenance(), contract.jasonPrograms(), List.of(environment), contract.moiseOrganizations(),
                contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("phase4-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }

    private static EnvironmentSemantic syntheticEnvironment(SemanticMetadata source) {
        String env = "phase4:environment";
        String root = "phase4:workspace:root";
        String child = "phase4:workspace:child";
        String type = "phase4:type";
        String artifact = "phase4:artifact";
        String operation = "phase4:operation";
        String property = "phase4:property";
        String agent = "phase4:agent";
        var workspaces = List.of(
                new WorkspaceSemantic(metadata(root, "CARTAGO_WORKSPACE"), "/main", "main", "w-root", "", env,
                        true, "local", "", ""),
                new WorkspaceSemantic(metadata(child, "CARTAGO_WORKSPACE"), "/main/child", "child", "w-child", root,
                        env, true, "local", "", ""));
        var artifacts = List.of(new ArtifactSemantic(metadata(artifact, "CARTAGO_ARTIFACT"), "artifact", "artifact-uuid",
                type, root, agent));
        var operations = List.of(new OperationDescriptorSemantic(metadata(operation, "CARTAGO_OPERATION_DESCRIPTOR"),
                artifact, "op-key", "operate", 1, true, false, false, false));
        var backing = List.of(new BackingJavaOperationSemantic(metadata("phase4:backing", "CARTAGO_BACKING_JAVA_OPERATION"),
                operation, "example.Artifact", "operate", List.of("java.lang.String"), "void", false, "loader"));
        var guards = List.of(new GuardSemantic(metadata("phase4:guard", "CARTAGO_GUARD"), operation, "guard", 0,
                "example.Guard"));
        var properties = List.of(new ObservablePropertySnapshotSemantic(metadata(property, "CARTAGO_OBSERVABLE_PROPERTY_SNAPSHOT"),
                artifact, "status#0", "status", List.of("READY"), List.of("java.lang.String"), List.of("public")));
        var infos = List.of(new ArtifactInfoSemantic(metadata("phase4:info", "CARTAGO_ARTIFACT_INFO"), artifact, agent,
                List.of(operation), List.of(property), List.of()));
        var signals = List.of(new SignalSemantic(metadata("phase4:signal", "CARTAGO_SIGNAL"), artifact, "changed",
                List.of("READY")));
        var agents = List.of(new CartagoAgentIdentitySemantic(metadata(agent, "CARTAGO_AGENT_IDENTITY"), "global-agent", 7,
                "agent", "worker", root));
        var focuses = List.of(
                new FocusSemantic(metadata("phase4:focus", "CARTAGO_FOCUS"), agent, artifact, true, 0),
                new FocusSemantic(metadata("phase4:unfocus", "CARTAGO_UNFOCUS"), agent, artifact, false, 1));
        return new EnvironmentSemantic(metadata(env, "CARTAGO_ENVIRONMENT"), "env", "env-id", "1.0", "default",
                workspaces, List.of(new ArtifactTypeSemantic(typeMetadata(type), "example.Artifact", "loader")), artifacts,
                operations, backing, guards, List.of(), properties, infos, signals, agents, focuses);
    }

    private static SemanticMetadata typeMetadata(String id) {
        return metadata(id, "CARTAGO_ARTIFACT_TYPE");
    }

    private static SemanticMetadata metadata(String id, String kind) {
        return new SemanticMetadata(id, kind, "cartago.synthetic.Phase4", EvidenceAuthority.OFFICIAL_CARTAGO_API,
                Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of());
    }
}
