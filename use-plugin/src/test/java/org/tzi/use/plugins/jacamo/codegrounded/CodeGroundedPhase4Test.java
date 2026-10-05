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
    @Test void observedArtifactStateUsesTypeObjectAttributesAndWorkspaceLink() throws Exception {
        var base=CodeGroundedTestFixtures.helloSnapshot(); var environment=syntheticEnvironment(base.semanticContract().project().metadata());
        var result=new CodeGroundedNativePipeline().build(withEnvironment(base,environment));
        var system=result.state().system(); var artifact=result.state().semanticObjectIndex().get("phase4:artifact");
        assertEquals("LiveRuntimePropertyArtifact",artifact.cls().name());
        assertEquals(base.semanticContract().workspaceDeclarations().size()+1,
                system.state().objectsOfClass(system.model().getClass("Workspace")).size());
        var rootDeclaration=base.semanticContract().workspaceDeclarations().stream()
                .filter(w->org.jacamo.bridge.contract.BridgeEntityId.parse(w.metadata().semanticId()).authority().equals("cartago"))
                .findFirst().orElseThrow();
        var root=result.state().semanticObjectIndex().get("phase4:workspace:root");
        assertSame(root,result.state().semanticObjectIndex().get(rootDeclaration.metadata().semanticId()));
        assertEquals("'w-root'",root.state(system.state()).attributeValue("uuid").toString());
        assertEquals("'READY'",artifact.state(system.state()).attributeValue("status").toString());
        assertEquals("'artifact-uuid'",artifact.state(system.state()).attributeValue("uuid").toString());
        assertEquals(1,links(system,org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("locatedIn","Workspace",artifact.cls().name())));
        for(String name:List.of("ArtifactType","ObservablePropertySnapshot","Environment","Operation","BackingJavaOperation","Guard","ArtifactInfo","Signal","CartagoAgentIdentity","LiveObservableProperty"))
            assertNull(system.model().getClass(name),name);
        assertTrue(artifact.cls().operations().isEmpty()); assertNull(artifact.cls().attribute("currentWinner",true));
        assertEquals(environment,result.source().snapshot().cartagoEnvironments().get(0));
        assertTrue(result.state().structureValid()); assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
    }
    @Test void livePropertyInputIsExplicitlyDiagnosedAndNeverConfusedWithObservedValues() throws Exception {
        var base=CodeGroundedTestFixtures.helloSnapshot(); var environment=syntheticEnvironment(base.semanticContract().project().metadata());
        var live=new org.jacamo.bridge.contract.semantic.CartagoSemanticContract.LiveObservablePropertySemantic(
            metadata("phase4:live","CARTAGO_LIVE_OBSERVABLE_PROPERTY"),"phase4:artifact","live","live",List.of("value"),List.of());
        var input=new EnvironmentSemantic(environment.metadata(),environment.name(),environment.environmentId(),environment.version(),environment.defaultInfrastructureLayer(),
            environment.workspaces(),environment.artifactTypes(),environment.artifacts(),environment.operations(),environment.backingOperations(),environment.guards(),List.of(live),
            environment.propertySnapshots(),environment.artifactInfos(),environment.signals(),environment.agents(),environment.focuses());
        var result=new CodeGroundedNativePipeline().build(withEnvironment(base,input));
        assertNull(result.model().model().getClass("LiveObservableProperty"));
        assertTrue(result.trace().records().stream().anyMatch(r -> r.ruleId().equals("C08") && r.targetKind().equals("Diagnostic")));
        assertEquals(List.of(live),result.source().snapshot().cartagoEnvironments().get(0).liveProperties());
    }
    @Test void artifactTypeReferenceRequiresExactSemanticIdentity() throws Exception {
        var base=CodeGroundedTestFixtures.helloSnapshot(); var environment=syntheticEnvironment(base.semanticContract().project().metadata());
        var bad=new ArtifactSemantic(metadata("phase4:bad-artifact","CARTAGO_ARTIFACT"),"artifact","artifact-uuid","unknown-type","phase4:workspace:root","phase4:agent");
        var input=new EnvironmentSemantic(environment.metadata(),environment.name(),environment.environmentId(),environment.version(),environment.defaultInfrastructureLayer(),
            environment.workspaces(),environment.artifactTypes(),List.of(bad),environment.operations(),environment.backingOperations(),environment.guards(),environment.liveProperties(),
            environment.propertySnapshots(),environment.artifactInfos(),environment.signals(),environment.agents(),environment.focuses());
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new CodeGroundedNativePipeline().build(withEnvironment(base,input))).getMessage().contains("C15_TYPE_REFERENCE"));
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
                operation, LiveRuntimePropertyArtifact.class.getName(), "operate", List.of("java.lang.String"), "void", false, "loader"));
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
                workspaces, List.of(new ArtifactTypeSemantic(typeMetadata(type), LiveRuntimePropertyArtifact.class.getName(), "loader")), artifacts,
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
