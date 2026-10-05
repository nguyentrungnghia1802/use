package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStateBuilder;

class CodeGroundedNegativeTest {
    @Test void duplicateSemanticIdentityFailsBeforeUseMaterialization() throws Exception {
        var original = CodeGroundedTestFixtures.helloSnapshot().semanticContract();
        var programs = new ArrayList<>(original.jasonPrograms());
        programs.add(programs.getFirst());
        var duplicate = copy(original, programs);
        var error = assertThrows(IllegalArgumentException.class, () -> new JacamoSpecificationModel(duplicate));
        assertTrue(error.getMessage().startsWith("DUPLICATE_SEMANTIC_IDENTITY:"));
    }

    @Test void unknownBodyTypeRemainsExplicitEvidenceWithoutDroppingTheNode() throws Exception {
        var original = CodeGroundedTestFixtures.helloSnapshot().semanticContract();
        var programs = new ArrayList<>(original.jasonPrograms());
        int programIndex = -1, planIndex = -1;
        for (int p = 0; p < programs.size() && programIndex < 0; p++)
            for (int q = 0; q < programs.get(p).planLibrary().plans().size(); q++)
                if (!programs.get(p).planLibrary().plans().get(q).body().isEmpty()) { programIndex = p; planIndex = q; break; }
        assertTrue(programIndex >= 0, "fixture must contain a plan body");
        var program = programs.get(programIndex);
        var plans = new ArrayList<>(program.planLibrary().plans());
        var plan = plans.get(planIndex);
        var bodies = new ArrayList<>(plan.body());
        var body = bodies.getFirst();
        bodies.set(0, new JasonSemanticContract.PlanBodyElementSemantic(body.metadata(), body.ordinal(),
                "futureBodyType", body.term(), body.nextSemanticId()));
        plans.set(planIndex, new JasonSemanticContract.PlanSemantic(plan.metadata(), plan.ordinal(), plan.label(),
                plan.context(), plan.trigger(), bodies));
        var library = new JasonSemanticContract.PlanLibrarySemantic(program.planLibrary().metadata(), plans);
        programs.set(programIndex, new JasonSemanticContract.AgentProgramSemantic(program.metadata(),
                program.declarationId(), program.sourceUri(), program.sourceDigest(), library));
        var source = new JacamoSpecificationModel(copy(original, programs));
        var trace = new CodeGroundedTraceCollector();
        var model = new NativeUseModelBuilder().build(source, trace);
        var state = new NativeUseStateBuilder().build(source, model, trace);
        assertNull(model.model().getClass("PlanBodyElement"));
        assertTrue(state.trace().records().stream().anyMatch(r -> r.sourceIdentity().equals(body.metadata().semanticId())
            && r.diagnostics().contains("JASON_BODY_TYPE_UNSUPPORTED_EVIDENCE_ONLY:futureBodyType")));
    }

    private static JacamoSemanticSnapshot copy(JacamoSemanticSnapshot source,
                                                List<JasonSemanticContract.AgentProgramSemantic> programs) {
        return new JacamoSemanticSnapshot(source.contractVersion(), source.project(), source.agentDeclarations(),
                source.workspaceDeclarations(), source.artifactDeclarations(), source.organizationDeployments(),
                source.groupDeployments(), source.schemeDeployments(), source.institutionDeployments(),
                source.rawRoleTuples(), source.rawFocusTuples(), source.importProvenance(), programs,
                source.cartagoEnvironments(), source.moiseOrganizations(), source.exactBindings(), source.diagnostics());
    }
}
