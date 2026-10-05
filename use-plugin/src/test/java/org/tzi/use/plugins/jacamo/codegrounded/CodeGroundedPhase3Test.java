package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.InstitutionDeploymentSemantic;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.ImportProvenanceSemantic;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.junit.jupiter.api.Test;

class CodeGroundedPhase3Test {
    @Test void jcmDeclarationsCreateDomainObjectsAndKeepOtherSourceReferences() throws Exception {
        var snapshot=withSyntheticInstitutionAndImport(CodeGroundedTestFixtures.helloSnapshot());
        var result=new CodeGroundedNativePipeline().build(snapshot,org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL);
        var system=result.state().system();
        assertEquals(snapshot.semanticContract().agentDeclarations().size(),system.state().allObjects().stream()
            .filter(o -> org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(o.cls()).equals("agent-program")).count());
        for(var deployment:snapshot.semanticContract().organizationDeployments())
            assertEquals("organisation",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(result.state().semanticObjectIndex().get(deployment.metadata().semanticId()).cls()));
        for(var deployment:snapshot.semanticContract().groupDeployments())
            assertEquals("group",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(result.state().semanticObjectIndex().get(deployment.metadata().semanticId()).cls()));
        for(String name:List.of("WorkspaceDeclaration","ArtifactDeclaration","OrganizationDeployment","GroupDeployment","SchemeDeployment","InstitutionDeployment","Soc"))
            assertNull(system.model().getClass(name));
        assertEquals(snapshot.semanticContract(),result.source().snapshot());
        for(String rule:List.of("J08","J09","J10","J11")) assertTrue(result.trace().records().stream().anyMatch(r -> r.ruleId().equals(rule)),rule);
        assertEquals("true",org.tzi.use.api.UseSystemApi.create(system,false).evaluate("Agent.allInstances()->size() = "+snapshot.semanticContract().agentDeclarations().size()).toString());
        assertTrue(result.state().structureValid()); assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
    }
    @Test void oneBaseAgentOwnsConcreteProgramTypesAndNoDeclarationWrappers() throws Exception {
        var result=CodeGroundedTestFixtures.helloPipeline();
        assertNotNull(result.model().model().getClass("Agent"));
        for(var cls:result.model().model().classes()) if(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(cls).equals("agent-program"))
            assertTrue(cls.parents().contains(result.model().model().getClass("Agent")));
        assertEquals(1,result.model().model().classes().stream().filter(c -> c.name().equals("Agent")).count());
        assertFalse(result.state().system().state().allObjects().stream().anyMatch(o -> o.cls().name().equals("Agent")));
    }
    private static ModelSnapshot withSyntheticInstitutionAndImport(ModelSnapshot base) {
        var contract = base.semanticContract();
        var source = contract.project().metadata();
        var institutionMetadata = metadata(source, "phase3:institution", "JACAMO_INSTITUTION_DEPLOYMENT",
                CapabilityStatus.PARTIAL, List.of("OPAQUE_RULE_ENGINE_NOT_SERIALIZED"));
        var importMetadata = metadata(source, "phase3:import", "JACAMO_IMPORT_PROVENANCE",
                CapabilityStatus.COMPLETE, List.of());
        var institutions = new ArrayList<>(contract.institutionDeployments());
        institutions.add(new InstitutionDeploymentSemantic(institutionMetadata, "phase3-institution",
                List.of("root"), java.util.Map.of("opaque", "preserved")));
        var imports = List.of(new ImportProvenanceSemantic(importMetadata, "project:/entry.jcm", "child.jcm",
                "project:/child.jcm", "digest-child", 0, CapabilityStatus.COMPLETE));
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                institutions, contract.rawRoleTuples(), contract.rawFocusTuples(), imports, contract.jasonPrograms(),
                contract.cartagoEnvironments(), contract.moiseOrganizations(), contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("phase3-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }

    private static SemanticMetadata metadata(SemanticMetadata base, String id, String kind,
                                             CapabilityStatus status, List<String> diagnostics) {
        return new SemanticMetadata(id, kind, base.sourceJavaFqcn(), EvidenceAuthority.OFFICIAL_JACAMO_API,
                Fidelity.EXACT, status, base.evidence(), diagnostics);
    }
}
