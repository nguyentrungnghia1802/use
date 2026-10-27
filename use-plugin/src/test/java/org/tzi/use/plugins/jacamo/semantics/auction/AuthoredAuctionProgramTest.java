package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;

/** Static/native candidate gate; external process/TCP acceptance remains a separate gate. */
class AuthoredAuctionProgramTest {
    @Test void authoredAuctionBuildsCanonicalModelsContractsAndRuntimePlanOnOneNativeModel()throws Exception {
        Path root=Path.of("examples/auction"),inputs=root.resolve("authored-v1");
        try(var fixture=new AuctionLiveFixture()) {
            var selection=MappingInputs.read(root.resolve("auction.jcm"),inputs.resolve("auction-mapping.xml"),inputs.resolve("auction_constraints_final_fixed.ocl"),inputs.resolve("auction_csl.csl"),inputs.resolve("auction_istar_final.istar"));
            var program=new GenericMappingEngine().map(fixture.source,selection);
            assertNull(program.casePlan());assertTrue(program.xmlPlan().inputs().authoredModels());
            assertEquals(4,program.goalModel().getActors().size());assertEquals(3,program.cslModel().roles().size());
            assertEquals(org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier.ANY,program.xmlPlan().dependencyPopulations().get("D_bid"));
            var built=new CodeGroundedNativePipeline().buildCanonicalProgram(fixture.source,NativeProjectionMode.AUTO,program);
            assertSame(program.cslModel(),built.binding().cslModel());assertSame(built.binding().model(),built.nativeResult().state().system().model());
            assertEquals(8,built.xmlPreparation().authored().clauses().size());assertEquals(12,built.contracts().contracts().size());
            assertEquals(3,built.xmlPreparation().operations().size());assertFalse(built.xmlPreparation().preflight().isEmpty());
            var scope=org.jacamo.bridge.contract.ObservationCaptureScope.fromMap(org.jacamo.bridge.contract.CanonicalJson.object(built.xmlPreparation().preflight().get("captureScope")));
            assertFalse(scope.allProperties());assertTrue(scope.propertyNames().containsAll(java.util.Set.of("running","task","best_bid","winner")));
            assertEquals(scope.hash(),built.xmlPreparation().preflight().get("captureScopeHash"));
            assertEquals(program.hash(),new SemanticProgramManifest().decode(new SemanticProgramManifest().encode(program)).program().hash());
        }
    }
}
