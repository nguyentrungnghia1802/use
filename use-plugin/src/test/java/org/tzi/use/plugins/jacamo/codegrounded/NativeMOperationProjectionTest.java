package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactTypeSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.BackingJavaOperationSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.CartagoAgentIdentitySemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.EnvironmentSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.FocusSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.WorkspaceSemantic;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.junit.jupiter.api.Test;

import cartago.Artifact;

class NativeMOperationProjectionTest {
    @Test
    void exactReflectionSignatureProducesNativeOperationAndConcreteArtifactType() throws Exception {
        String typeId = "native-op:type";
        String operationId = "native-op:operation";
        var result = new CodeGroundedNativePipeline().build(withEnvironment(environment(typeId, operationId, "openAuction")));

        String owner = result.model().nativeArtifactTypeClassNames().get(typeId);
        assertNotNull(owner);
        assertTrue(result.model().nativeOperationDescriptorIds().contains(operationId));
        assertNotNull(result.model().model().getClass(owner).operation("openAuction", false));
        assertTrue(result.state().system().state().objectsOfClass(result.model().model().getClass(owner)).size() == 1);
        assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals("C06")
                && value.targetKind().equals("MOperation")
                && value.diagnostics().contains("NATIVE_OPERATION_PROJECTED")));
        assertEquals(result.export().originalStructuralHash(), result.export().recompiledStructuralHash());
    }

    @Test
    void nonExactReflectionSignatureRemainsStructuralOperationOnly() throws Exception {
        var result = new CodeGroundedNativePipeline().build(withEnvironment(
                environment("native-op:type-negative", "native-op:operation-negative", "doesNotExist")));
        assertTrue(result.model().nativeOperationDescriptorIds().isEmpty());
        assertTrue(result.model().nativeArtifactTypeClassNames().isEmpty());
        assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals("C06")
                && value.diagnostics().contains("NATIVE_MOPERATION_NOT_PROJECTED")));
    }

    public static class ProjectionArtifact extends Artifact {
        public void openAuction() { }
    }

    private static EnvironmentSemantic environment(String typeId, String operationId, String methodName) {
        String environmentId = "native-op:environment";
        String workspaceId = "native-op:workspace";
        String artifactId = "native-op:artifact";
        String agentId = "native-op:agent";
        String className = ProjectionArtifact.class.getName();
        var operation = new OperationDescriptorSemantic(metadata(operationId, "CARTAGO_OPERATION_DESCRIPTOR"),
                artifactId, "open-key", "openAuction", 0, false, false, false, false);
        var backing = new BackingJavaOperationSemantic(metadata("native-op:backing:" + methodName,
                "CARTAGO_BACKING_JAVA_OPERATION"), operationId, className, methodName, List.of(), "void", false, "test");
        return new EnvironmentSemantic(metadata(environmentId, "CARTAGO_ENVIRONMENT"), "environment", "environment-id",
                "1.0", "default", List.of(new WorkspaceSemantic(metadata(workspaceId, "CARTAGO_WORKSPACE"),
                "/main", "main", "workspace-uuid", "", environmentId, true, "local", "", "")),
                List.of(new ArtifactTypeSemantic(metadata(typeId, "CARTAGO_ARTIFACT_TYPE"), className, "test")),
                List.of(new ArtifactSemantic(metadata(artifactId, "CARTAGO_ARTIFACT"), "artifact", "artifact-uuid",
                        typeId, workspaceId, agentId)), List.of(operation), List.of(backing), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                List.of(new CartagoAgentIdentitySemantic(metadata(agentId, "CARTAGO_AGENT_IDENTITY"),
                        "global-agent", 1, "agent", "worker", workspaceId)),
                List.of(new FocusSemantic(metadata("native-op:focus", "CARTAGO_FOCUS"), agentId, artifactId, true, 0)));
    }

    private static ModelSnapshot withEnvironment(EnvironmentSemantic environment) throws Exception {
        ModelSnapshot base = CodeGroundedTestFixtures.helloSnapshot();
        var contract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                contract.institutionDeployments(), contract.rawRoleTuples(), contract.rawFocusTuples(),
                contract.importProvenance(), contract.jasonPrograms(), List.of(environment), contract.moiseOrganizations(),
                contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("native-operation-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }

    private static SemanticMetadata metadata(String id, String kind) {
        return new SemanticMetadata(id, kind, ProjectionArtifact.class.getName(), EvidenceAuthority.OFFICIAL_CARTAGO_API,
                Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of());
    }
}
