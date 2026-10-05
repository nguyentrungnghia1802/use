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

        assertEquals(program,result.source().programs().stream().filter(p -> p.metadata().semanticId().equals(program.metadata().semanticId())).findFirst().orElseThrow());
        assertEquals(bodySize,result.trace().records().stream().filter(r -> r.ruleId().equals("A05")
            && program.planLibrary().plans().getFirst().body().stream().anyMatch(b -> b.metadata().semanticId().equals(r.sourceIdentity()))).count());
        assertEquals(bodySize-1,program.planLibrary().plans().getFirst().body().stream().filter(b -> !b.nextSemanticId().isEmpty()).count());
        assertEquals(null,system.model().getClass("PlanBodyElement"));
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
                contract.importProvenance(), java.util.stream.Stream.concat(contract.jasonPrograms().stream(),java.util.stream.Stream.of(program)).toList(), contract.cartagoEnvironments(),
                contract.moiseOrganizations(), contract.exactBindings(), contract.diagnostics());
        return new ModelSnapshot("large-plan-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }
}
