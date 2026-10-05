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
    void exactReflectionSignatureBecomesNativeOperationOnConcreteArtifact() throws Exception {
        String typeId = "native-op:type";
        String operationId = "native-op:operation";
        var result = new CodeGroundedNativePipeline().build(withEnvironment(environment(typeId, operationId, "openAuction")));

        String owner = result.model().nativeArtifactTypeClassNames().get(typeId);
        assertNotNull(owner);
        assertEquals(java.util.Set.of(operationId),result.model().nativeOperationDescriptorIds());
        assertEquals(1,result.model().model().getClass(owner).operations().size());
        assertTrue(result.state().system().state().objectsOfClass(result.model().model().getClass(owner)).size() == 1);
        assertEquals(1, result.source().snapshot().cartagoEnvironments().getFirst().backingOperations().size());
        assertTrue(result.trace().records().stream().anyMatch(value -> value.targetKind().equals("MOperation")));
        assertEquals(result.export().originalStructuralHash(), result.export().recompiledStructuralHash());
        var engine=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine(result.state().system(),result.state().semanticObjectIndex(),
                new org.tzi.use.plugins.jacamo.codegrounded.runtime.CodeGroundedRuntimeRuleRegistry());
        GenericFunctionalRuntimeProjectionTest.assertBindingsComplete(engine);
        assertTrue(engine.targetBindings().stream().anyMatch(b->b.kind().equals("MOperation") && b.sourceIdentity().equals(operationId)));
    }

    @Test void exactFocusAndJoinBindingsAreIndependentManyToManyRelations() throws Exception {
        var observed=environment("generic:type","generic:operation","openAuction");
        var secondWorkspace=new WorkspaceSemantic(metadata("generic:workspace-two","CARTAGO_WORKSPACE"),"/second","second","workspace-two-uuid","",observed.metadata().semanticId(),true,"local","","");
        var secondArtifact=new ArtifactSemantic(metadata("generic:artifact-two","CARTAGO_ARTIFACT"),"second-artifact","artifact-two-uuid","generic:type",secondWorkspace.metadata().semanticId(),"");
        var twoWorkspaces=new EnvironmentSemantic(observed.metadata(),observed.name(),observed.environmentId(),observed.version(),observed.defaultInfrastructureLayer(),
                List.of(observed.workspaces().getFirst(),secondWorkspace),observed.artifactTypes(),List.of(observed.artifacts().getFirst(),secondArtifact),observed.operations(),observed.backingOperations(),
                observed.guards(),observed.liveProperties(),observed.propertySnapshots(),observed.artifactInfos(),observed.signals(),observed.agents(),observed.focuses());
        var snapshot=withEnvironment(twoWorkspaces);
        var source=snapshot.semanticContract();
        var agents=source.agentDeclarations().stream().limit(2).map(a->a.metadata().semanticId()).toList();
        var environment=source.cartagoEnvironments().getFirst();
        var focus=new org.jacamo.bridge.contract.semantic.CrossSemanticContract.ExactBindingSemantic(metadata("generic:focus","CARTAGO_FOCUS"),"X06",agents,
                environment.artifacts().stream().map(a->a.metadata().semanticId()).toList(),java.util.Map.of());
        var bindings=new java.util.ArrayList<>(source.exactBindings().stream().filter(b->!java.util.Set.of("X05","X06").contains(b.ruleId())).toList());
        bindings.add(focus);
        var focused=new CodeGroundedNativePipeline().build(withBindings(snapshot,bindings)); var system=focused.state().system();
        var joins=system.model().getAssociation(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("memberOf","Agent","Workspace"));
        var focuses=system.model().getAssociation(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("focuses","Agent","Artifact"));
        for(var association:List.of(joins,focuses)) for(var end:association.associationEnds()) assertEquals("*",end.multiplicity().toString());
        assertEquals(4,system.state().allLinks().stream().filter(l->l.association()==focuses).count());
        assertEquals(0,system.state().allLinks().stream().filter(l->l.association()==joins).count(),"focus must not invent workspace membership");
        bindings.add(new org.jacamo.bridge.contract.semantic.CrossSemanticContract.ExactBindingSemantic(metadata("generic:joins","CARTAGO_WORKSPACE_JOIN"),"X05",agents,
                environment.workspaces().stream().map(w->w.metadata().semanticId()).toList(),java.util.Map.of()));
        var joined=new CodeGroundedNativePipeline().build(withBindings(snapshot,bindings));
        assertEquals(4,joined.state().system().state().allLinks().stream().filter(l->l.association().name().equals(joins.name())).count());
        assertEquals(4,joined.state().system().state().allLinks().stream().filter(l->l.association().name().equals(focuses.name())).count());
    }

    @Test
    void nonExactReflectionSignatureCannotCreateAnOperationOrSuppressArtifactState() throws Exception {
        var result = new CodeGroundedNativePipeline().build(withEnvironment(
                environment("native-op:type-negative", "native-op:operation-negative", "doesNotExist")));
        assertTrue(result.model().nativeOperationDescriptorIds().isEmpty());
        assertEquals(1, result.model().nativeArtifactTypeClassNames().size());
        assertTrue(result.model().model().classes().stream().allMatch(value -> value.operations().isEmpty()));
        assertFalse(result.trace().records().stream().anyMatch(value -> value.targetKind().equals("MOperation")));
    }

    public static class ProjectionArtifact extends Artifact {
        @cartago.OPERATION
        public void openAuction() { }
        @cartago.OPERATION public int adjust(int count,boolean enabled,String label,double cost) { return count; }
        @cartago.OPERATION public void adjust(String label) { }
        @cartago.OPERATION public void opaque(Object value) { }
    }
    @Test void parameterReturnAndOverloadIdentityAreExactAndUnknownTypeFailsClosed() throws Exception {
        var pipeline=new CodeGroundedNativePipeline().build(withEnvironment(environment("generic:type","generic:operation","openAuction")));
        var model=pipeline.model().model(); var api=new org.tzi.use.api.UseModelApi(model);
        String owner=pipeline.model().nativeArtifactTypeClassNames().get("generic:type");
        var descriptor=new OperationDescriptorSemantic(metadata("generic:adjust","CARTAGO_OPERATION_DESCRIPTOR"),"native-op:artifact","adjust/4","adjust",4,false,false,false,false);
        var backing=new BackingJavaOperationSemantic(metadata("generic:backing","CARTAGO_BACKING_JAVA_OPERATION"),descriptor.metadata().semanticId(),ProjectionArtifact.class.getName(),"adjust",List.of("int","boolean","java.lang.String","double"),"int",false,"actual-method");
        var operation=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.operation(api,owner,descriptor,backing);
        assertEquals("Integer",operation.resultType().toString());
        assertTrue(operation.signature().contains("p0 : Integer"),operation.signature()); assertTrue(operation.signature().contains("p1 : Boolean"),operation.signature());
        assertTrue(operation.signature().contains("p2 : String"),operation.signature()); assertTrue(operation.signature().contains("p3 : Real"),operation.signature());
        var overload=new OperationDescriptorSemantic(metadata("generic:adjust-one","CARTAGO_OPERATION_DESCRIPTOR"),"native-op:artifact","adjust/1","adjust",1,false,false,false,false);
        var second=new BackingJavaOperationSemantic(metadata("generic:backing-one","CARTAGO_BACKING_JAVA_OPERATION"),overload.metadata().semanticId(),ProjectionArtifact.class.getName(),"adjust",List.of("java.lang.String"),"void",false,"actual-method");
        assertNotEquals(operation.name(),org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.operation(api,owner,overload,second).name());
        var opaque=new OperationDescriptorSemantic(metadata("generic:opaque","CARTAGO_OPERATION_DESCRIPTOR"),"native-op:artifact","opaque/1","opaque",1,false,false,false,false);
        var erased=new BackingJavaOperationSemantic(metadata("generic:erased","CARTAGO_BACKING_JAVA_OPERATION"),opaque.metadata().semanticId(),ProjectionArtifact.class.getName(),"opaque",List.of("java.lang.Object"),"void",false,"actual-method");
        int before=model.getClass(owner).operations().size();
        assertThrows(IllegalArgumentException.class,()->org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.operation(api,owner,opaque,erased));
        assertEquals(before,model.getClass(owner).operations().size());
        assertDoesNotThrow(()->new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter().export(model));
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

    private static ModelSnapshot withBindings(ModelSnapshot base,List<org.jacamo.bridge.contract.semantic.CrossSemanticContract.ExactBindingSemantic> bindings) {
        var c=base.semanticContract();
        var semantic=new JacamoSemanticSnapshot(c.contractVersion(),c.project(),c.agentDeclarations(),c.workspaceDeclarations(),c.artifactDeclarations(),
                c.organizationDeployments(),c.groupDeployments(),c.schemeDeployments(),c.institutionDeployments(),c.rawRoleTuples(),c.rawFocusTuples(),
                c.importProvenance(),c.jasonPrograms(),c.cartagoEnvironments(),c.moiseOrganizations(),bindings,c.diagnostics());
        return new ModelSnapshot(base.modelRevision(),base.sources(),base.agentDeclarations(),base.workspaces(),base.configuredArtifacts(),base.organisationFacts(),
                base.groupRoleCardinalities(),base.parentSubGroupCardinalities(),base.crossDimensionalRelations(),base.unresolvedFacts(),base.projectionProvenance(),semantic);
    }

    private static SemanticMetadata metadata(String id, String kind) {
        return new SemanticMetadata(id, kind, ProjectionArtifact.class.getName(), EvidenceAuthority.OFFICIAL_CARTAGO_API,
                Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of(), List.of());
    }
}
