package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jacamo.bridge.adapter.OfficialJasonAdapter;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeGroundedLargePlanTest {
    @TempDir Path temporary;

    @Test
    void nativeBuilderRetainsA_largeOfficialJasonBodyAndItsOrder() throws Exception {
        Path source = temporary.resolve("large.asl");
        int bodySize = 256;
        String body = IntStream.range(0, bodySize).mapToObj(index -> "act" + index + "(item)")
                .collect(Collectors.joining("; "));
        Files.writeString(source, "+!boot <- " + body + ".\n");

        AgentProgramSemantic program = new OfficialJasonAdapter().adapt(temporary, source, "large", "agent").program();
        assertEquals(bodySize, program.planLibrary().plans().getFirst().body().size());
        var result = new CodeGroundedNativePipeline().build(withProgram(CodeGroundedTestFixtures.helloSnapshot(), program));
        var system = result.state().system();

        assertEquals(bodySize, system.state().objectsOfClass(system.model().getClass("PlanBodyElement")).size());
        assertEquals(bodySize - 1,
                system.state().linksOfAssociation(system.model().getAssociation("A20PlanBodyNext")).size());
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
        assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
    }

    private static ModelSnapshot withProgram(ModelSnapshot base, AgentProgramSemantic program) {
        var contract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(contract.contractVersion(), contract.project(),
                contract.agentDeclarations(), contract.workspaceDeclarations(), contract.artifactDeclarations(),
                contract.organizationDeployments(), contract.groupDeployments(), contract.schemeDeployments(),
                contract.institutionDeployments(), contract.rawRoleTuples(), contract.rawFocusTuples(),
                contract.importProvenance(), List.of(program), contract.cartagoEnvironments(),
                contract.moiseOrganizations(), contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("large-plan-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }
}
