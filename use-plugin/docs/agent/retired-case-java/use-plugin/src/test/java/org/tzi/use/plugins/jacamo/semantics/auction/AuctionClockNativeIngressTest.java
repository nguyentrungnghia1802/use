package org.tzi.use.plugins.jacamo.semantics.auction;

import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.adapter.AuctionClockObservation;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionClockNativeIngressTest {
    @Test void idleClockBoundaryUsesExistingOneStateAndFrozenAnchorCannotChangeAcrossEventsOrResync()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            var frames=new ArrayList<VerificationObservation>();
            try(var clock=new AuctionClockObservation(live.run.id(),live.run.generation(),live.source.modelRevision(),frames::add)){clock.pulse(false);}
            var frame=frames.getFirst();var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                var cut=live.cut();projector.applySnapshot(cut,live.run.id(),live.run.generation());var state=projector.system().state();var before=projector.mutations().savepoint();
                var event=frame.event(live.run.id(),live.run.generation(),live.source.modelRevision(),1,Instant.EPOCH,"","");assertFalse(projector.apply(event));
                assertSame(state,projector.system().state());assertEquals(before,projector.mutations().savepoint());assertTrue(projector.evidence().containsKey(event.entityId().canonical()));
                var metrics=projector.coordinator().performanceMetrics();long count=((Number)metrics.get("verificationCount")).longValue();
                var boundary=new RuntimeEvent("clock-boundary",live.run.id(),live.run.generation(),live.source.modelRevision(),"bridge",VerificationBoundary.SOURCE_ID,1,Instant.EPOCH,RuntimeEventKind.STREAM_BOUNDARY,
                        RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("bridge","runtime","verification-boundary",live.run.id(),"batch",event.eventId()),null,event.eventId(),event.eventId(),Map.of(),
                        Map.of("boundarySource",event.sourceId(),"boundaryEventId",event.eventId(),"boundarySourceSequence",event.sourceSequence()),new SourceWatermark(VerificationBoundary.SOURCE_ID,1),Completeness.COMPLETE,List.of());
                assertFalse(projector.apply(boundary));assertTrue(((Number)projector.coordinator().performanceMetrics().get("verificationCount")).longValue()>count);
                assertSame(state,projector.system().state());assertEquals(before,projector.mutations().savepoint());
                var missingMarks=new TreeMap<>(cut.endWatermarks());missingMarks.put(VerificationObservation.SOURCE_ID,event.watermark());missingMarks.put(VerificationBoundary.SOURCE_ID,boundary.watermark());
                var missingClock=new RuntimeSnapshot("partial-without-clock",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),missingMarks,missingMarks,1,cut.facts(),cut.sourceCompleteness(),"test-missing-clock");
                projector.applySnapshot(missingClock,live.run.id(),live.run.generation());
                assertFalse(projector.evidence().containsKey(event.entityId().canonical()),"An absent current observation must remain unavailable");
                var changed=AuctionStepDecoderTest.variant(frame,data->{var proof=AuctionStepDecoderTest.map(data.get("sourceProof"));proof.put("anchorEpochMillis",Long.toString(Long.parseLong((String)proof.get("anchorEpochMillis"))-1));});
                var facts=new ArrayList<>(cut.facts());var changedEvent=changed.event(live.run.id(),live.run.generation(),live.source.modelRevision(),2,Instant.EPOCH,"","");
                facts.add(new RuntimeFact(changedEvent.entityId(),changedEvent.factKind(),changedEvent.after(),List.of(),changedEvent.projectionStatus(),changedEvent.completeness(),List.of()));
                var marks=new TreeMap<>(cut.endWatermarks());marks.put(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,2));marks.put(VerificationBoundary.SOURCE_ID,new SourceWatermark(VerificationBoundary.SOURCE_ID,1));
                var changedCut=new RuntimeSnapshot("changed-anchor",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),marks,marks,1,facts,cut.sourceCompleteness(),"test-anchor-change");
                assertTrue(assertThrows(IllegalArgumentException.class,()->projector.applySnapshot(changedCut,live.run.id(),live.run.generation())).getMessage().contains("ANCHOR_CHANGED"));
                assertEquals(before,projector.mutations().savepoint());
                assertTrue(assertThrows(IllegalArgumentException.class,()->projector.apply(changedEvent)).getMessage().contains("ANCHOR_CHANGED"));assertTrue(projector.resyncRequired());
                assertEquals(before,projector.mutations().savepoint());assertFalse(projector.evidence().containsKey(event.entityId().canonical()),"Retained anchor proof cannot masquerade as a current clock observation");
            }
        }
    }
}
