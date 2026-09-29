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
    @Test void nativePhase3MaterializesJcmDeclarationsAndRetainsRawReferences() throws Exception {
        var base = CodeGroundedTestFixtures.helloSnapshot();
        var snapshot = withSyntheticInstitutionAndImport(base);
        var result = new CodeGroundedNativePipeline().build(snapshot);
        var system = result.state().system();

        assertEquals(snapshot.semanticContract().agentDeclarations().size(),
                system.state().objectsOfClass(system.model().getClass("Agent")).size());
        assertEquals(snapshot.semanticContract().workspaceDeclarations().size(),
                system.state().objectsOfClass(system.model().getClass("WorkspaceDeclaration")).size());
        assertEquals(snapshot.semanticContract().artifactDeclarations().size(),
                system.state().objectsOfClass(system.model().getClass("ArtifactDeclaration")).size());
        assertEquals(snapshot.semanticContract().organizationDeployments().size(),
                system.state().objectsOfClass(system.model().getClass("OrganizationDeployment")).size());
        assertEquals(snapshot.semanticContract().groupDeployments().size(),
                system.state().objectsOfClass(system.model().getClass("GroupDeployment")).size());
        assertEquals(snapshot.semanticContract().schemeDeployments().size(),
                system.state().objectsOfClass(system.model().getClass("SchemeDeployment")).size());
        assertEquals(snapshot.semanticContract().institutionDeployments().size(),
                system.state().objectsOfClass(system.model().getClass("InstitutionDeployment")).size());

        assertNotNull(system.model().getClass("Artifact"), "the native schema reserves a distinct CArtAgO runtime class");
        assertEquals(0, system.state().objectsOfClass(system.model().getClass("Artifact")).size(),
                "JCM declarations must not fabricate live CArtAgO artifacts");
        assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals("J09")
                && value.targetKind().equals("RAW_ROLE_TUPLE")));
        assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals("J10")
                && value.targetKind().equals("RAW_FOCUS_TUPLE")));
        assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals("J11")
                && value.targetKind().equals("IMPORT_PROVENANCE")));
        assertFalse(result.trace().records().stream().anyMatch(value ->
                (value.ruleId().equals("J09") || value.ruleId().equals("J10"))
                        && value.targetKind().equals("MLink")));

        String query = "Agent.allInstances->size() = " + snapshot.semanticContract().agentDeclarations().size()
                + " and WorkspaceDeclaration.allInstances->size() = " + snapshot.semanticContract().workspaceDeclarations().size()
                + " and ArtifactDeclaration.allInstances->size() = "
                + snapshot.semanticContract().artifactDeclarations().size();
        assertEquals("true", org.tzi.use.api.UseSystemApi.create(system, false).evaluate(query).toString());
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
        assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
    }

    @Test void phase3SchemaContainsAllDeploymentTypesWithoutRuntimeCollapse() throws Exception {
        var result = CodeGroundedTestFixtures.helloPipeline();
        for (String className : List.of("Agent", "WorkspaceDeclaration", "ArtifactDeclaration", "OrganizationDeployment",
                "GroupDeployment", "SchemeDeployment", "InstitutionDeployment"))
            assertNotNull(result.model().model().getClass(className), className);
        assertNotNull(result.model().model().getClass("Artifact"));
        assertEquals(0, result.state().system().state()
                .objectsOfClass(result.model().model().getClass("Artifact")).size());
        for (String ruleId : List.of("J02", "J03", "J04", "J05", "J06", "J07", "J08", "J09", "J10", "J11"))
            assertTrue(result.trace().records().stream().anyMatch(value -> value.ruleId().equals(ruleId)), ruleId);
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
