package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jacamo.bridge.adapter.OfficialJasonAdapter;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.PlanLibrarySemantic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeGroundedPhase2Test {
    @TempDir Path temporary;

    @Test void nativePhase2MaterializesJasonFactsAndA21A22OnTheSameSystem() throws Exception {
        Path source = temporary.resolve("phase2.asl");
        Files.writeString(source, "belief(a).\n"
                + "eligible(X) :- available(X).\n"
                + "!boot.\n"
                + "+!boot <- external(a); .print(\"ok\").\n");
        var program = new OfficialJasonAdapter().adapt(temporary, source, "phase2", "agent").program();
        var base = CodeGroundedTestFixtures.helloSnapshot();
        var snapshot = withProgram(base, program);

        var result = new CodeGroundedNativePipeline().build(snapshot);
        var system = result.state().system();
        assertEquals(2, system.state().objectsOfClass(system.model().getClass("Action")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("Belief")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("AgentGoal")).size());
        assertEquals(1, system.state().objectsOfClass(system.model().getClass("BeliefRule")).size());
        assertEquals(1, system.state().linksOfAssociation(system.model().getAssociation("A21ProgramBelief")).size());
        assertEquals(1, system.state().linksOfAssociation(system.model().getAssociation("A22ProgramGoal")).size());
        assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals("A06")));
        assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals("A07")));
        assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals("A08")));
        assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals("A09")));
        assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals("A10")));
        assertTrue(result.trace().records().stream().anyMatch(record -> record.ruleId().equals("A11")));
        assertEquals("true", org.tzi.use.api.UseSystemApi.create(system, false).evaluate(
                "AgentProgram.allInstances->forAll(p | p.initialBeliefs->size() = 1 and "
                        + "p.initialGoals->size() = 1)").toString());
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
        assertEquals(result.model().structuralHash(), result.export().recompiledStructuralHash());
    }

    @Test void emptyPlanLibraryIsAValidOfficialJasonSnapshot() throws Exception {
        Path source = temporary.resolve("empty.asl");
        Files.writeString(source, "belief(a).\n");
        var program = new OfficialJasonAdapter().adapt(temporary, source, "phase2-empty", "agent").program();
        var empty = new AgentProgramSemantic(program.metadata(), program.declarationId(), program.sourceUri(),
                program.sourceDigest(), new PlanLibrarySemantic(program.planLibrary().metadata(), List.of()),
                List.of(), program.beliefs(), program.goals(), program.beliefRules());
        var result = new CodeGroundedNativePipeline().build(withProgram(CodeGroundedTestFixtures.helloSnapshot(), empty));
        var system = result.state().system();
        assertEquals(0, system.state().objectsOfClass(system.model().getClass("Plan")).size());
        assertTrue(result.state().structureValid());
        assertTrue(result.state().invariantsValid());
    }

    private static ModelSnapshot withProgram(ModelSnapshot base, AgentProgramSemantic program) {
        var baseContract = base.semanticContract();
        var semantic = new JacamoSemanticSnapshot(baseContract.contractVersion(), baseContract.project(),
                baseContract.agentDeclarations(), baseContract.workspaceDeclarations(), baseContract.artifactDeclarations(),
                baseContract.organizationDeployments(), baseContract.groupDeployments(), baseContract.schemeDeployments(),
                baseContract.institutionDeployments(), baseContract.rawRoleTuples(), baseContract.rawFocusTuples(),
                baseContract.importProvenance(), List.of(program), baseContract.cartagoEnvironments(),
                baseContract.moiseOrganizations(), baseContract.exactBindings(), baseContract.diagnostics());
        return new ModelSnapshot("phase2-test", base.sources(), base.agentDeclarations(), base.workspaces(),
                base.configuredArtifacts(), base.organisationFacts(), base.groupRoleCardinalities(),
                base.parentSubGroupCardinalities(), base.crossDimensionalRelations(), base.unresolvedFacts(),
                base.projectionProvenance(), semantic);
    }
}
