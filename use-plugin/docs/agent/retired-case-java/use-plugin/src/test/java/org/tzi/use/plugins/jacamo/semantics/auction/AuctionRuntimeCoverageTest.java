package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionStaticMappingAdapter;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.Value;
import static org.junit.jupiter.api.Assertions.*;

class AuctionRuntimeCoverageTest {
    @Test void aPhysicalButUnboundArtifactCannotCertifyAnEmptyCanonicalPopulation()throws Exception {
        try(var live=new AuctionLiveFixture(true)) {
            live.auction("a1");
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision());var ocl=new NativeOclBackend(pipeline.binding().model(),"R02-population","AUCTION_XML/1.0")) {
                projector.applySnapshot(live.cut(),live.run.id(),live.run.generation());
                assertEquals(0,projector.system().state().objectsOfClass(pipeline.binding().model().getClass("Auction")).size());
                var expression=ocl.compile(new OclContractSpec("empty",OclContractSpec.Kind.INVARIANT,pipeline.binding().model().getClass("Auction"),null,"Auction.allInstances()->isEmpty()"));
                String missing=new ConstraintCoverageResolver().unavailable(expression.expression(),projector.mutations(),Map.of("cartago",Completeness.COMPLETE),false);
                assertFalse(missing.isBlank(),"Complete raw Cartago enumeration does not prove complete CSL bindings");
                assertEquals(NativeOclOutcome.Status.SKIPPED,ocl.evaluate(expression,UseStateReadView.pin(null,projector.system().state()),Map.<String,Value>of(),missing).status());
            }
        }
    }
    @Test void anUnresolvedCrosswalkInvalidatesItsOwnStateWithoutSuppressingAnIndependentSelfCheck()throws Exception {
        try(var fixture=new AuctionOrgFixture()) {
            var live=fixture.live;fixture.group();fixture.scheme("a1","service(one)");fixture.scheme("a2","service(two)");live.auction("a1");live.auction("a2");
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision());var ocl=new NativeOclBackend(pipeline.binding().model(),"R02-crosswalk","AUCTION_XML/1.0")) {
                projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());
                var a=AuctionRuntimeProjectionTest.auction(projector,"a1");var b=AuctionRuntimeProjectionTest.auction(projector,"a2");var cut=fixture.cut();
                var facts=cut.facts().stream().filter(f->f.kind()!=RuntimeFactKind.SCHEME_BOARD||!"sch_a1".equals(f.values().get("name"))).toList();
                assertEquals(cut.facts().size()-1,facts.size(),"Remove the actual a1 scheme proof, not a guessed source alias");
                projector.applySnapshot(new RuntimeSnapshot(cut.snapshotId()+":missing-crosswalk",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,facts,cut.sourceCompleteness(),cut.stateFingerprint()+":missing-crosswalk"),live.run.id(),live.run.generation());
                assertTrue(a.state(projector.system().state()).attributeValue("running").isUndefined(),"Old observed values cannot certify an unresolved incarnation");
                var running=ocl.compile(new OclContractSpec("running",OclContractSpec.Kind.INVARIANT,a.cls(),null,"self.running"));var coverage=new ConstraintCoverageResolver();var sources=Map.of("cartago",Completeness.COMPLETE);
                assertFalse(coverage.unavailableSelf(running.expression(),projector.mutations(),a,sources,false).isBlank());
                assertEquals("",coverage.unavailableSelf(running.expression(),projector.mutations(),b,sources,false));
                assertEquals(NativeOclOutcome.Status.FAIL,ocl.evaluate(running,UseStateReadView.pin(null,projector.system().state()),Map.of("self",b.value()),"").status(),"The other instance's observed false remains a real Boolean outcome");
                projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());assertEquals("COMPLETE",projector.mutations().metadata(a,"auctionFullState"));
            }
        }
    }
}
