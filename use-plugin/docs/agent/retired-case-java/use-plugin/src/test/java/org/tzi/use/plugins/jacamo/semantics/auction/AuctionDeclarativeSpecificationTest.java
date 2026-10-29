package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;

/** Auction is specification/test data here; the production preparation has no case adapter. */
class AuctionDeclarativeSpecificationTest {
    @TempDir Path temp;
    static final Path CASE=Path.of("examples/auction");
    static MappingInputs inputs(Path root) {
        return MappingInputs.read(root.resolve("auction.jcm"),root.resolve("auction-mapping.xml"),root.resolve("auction_constraints_final.ocl"));
    }
    @Test void projectXmlAndAuthoredOclBuildGeneratedModelsAndAllNativeTaskContracts()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var program=new GenericMappingEngine().map(fixture.source,inputs(CASE));
            assertNull(program.casePlan());assertNotNull(program.xmlPlan());
            assertEquals(8,program.xmlPlan().requirements().size());
            for(var request:program.xmlPlan().requirements())switch(request.provider()) {
                case "cartago.artifact-field.v1" -> assertNotNull(new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.ArtifactFieldCutDecoder(fixture.source,request));
                case "moise.scheme-lifecycle.v1" -> assertNotNull(new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.MoiseSchemeCutDecoder(fixture.source,request));
                case "cartago.operation-history.v1" -> assertNotNull(new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.OperationHistoryCutDecoder(request));
                case "jason.plan-boundaries.v1" -> assertNotNull(new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.JasonStepCutDecoder(fixture.source,request));
                default -> assertTrue(Set.of("npl.source-clock.v1","npl.lifecycle.v1").contains(request.provider()));
            }

            var built=new CodeGroundedNativePipeline().buildCanonicalProgram(fixture.source,NativeProjectionMode.AUTO,program);
            assertSame(program.cslModel(),built.binding().cslModel());
            assertSame(built.binding().model(),built.nativeResult().state().system().model());
            assertEquals(3,built.xmlPreparation().operations().size());
            assertEquals(8,built.xmlPreparation().authored().clauses().size());
            assertEquals(14,built.contracts().contracts().size(),"Two generated CSL invariants and twelve original Task predicate slots");
            assertTrue(program.complement().assets().stream().noneMatch(a->Set.of("target-csl","target-istar","target-drawio","istar-drawio","case-manifest").contains(a.id())));
        }
    }
    @Test void generatedProgramNeedsNeitherManifestNorModelOracles()throws Exception {
        var original=inputs(CASE);
        for(var file:original.specification().root().child("sources").children("file")) {
            Path target=temp.resolve(file.attr("path"));Files.createDirectories(target.getParent());Files.copy(CASE.resolve(file.attr("path")),target);
        }
        for(String file:List.of("auction-mapping.xml","auction_constraints_final.ocl"))Files.copy(CASE.resolve(file),temp.resolve(file));
        try(var fixture=new AuctionLiveFixture()) {
            var first=new GenericMappingEngine().map(fixture.source,inputs(temp));
            for(String oracle:List.of("auction_csl.csl","auction_istar_final.istar","auction_csl.drawio","case-manifest.json"))Files.writeString(temp.resolve(oracle),"deliberately invalid oracle");
            var second=new GenericMappingEngine().map(fixture.source,inputs(temp));
            assertEquals(first.hash(),second.hash());assertEquals(first.cslHash(),second.cslHash());assertEquals(first.goalHash(),second.goalHash());
        }
    }
}
