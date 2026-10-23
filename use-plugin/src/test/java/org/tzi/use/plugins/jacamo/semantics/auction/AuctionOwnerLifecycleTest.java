package org.tzi.use.plugins.jacamo.semantics.auction;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit source-contract component inputs on actual Auction native state.
 * Producer/process and public facade lifecycle are separate live tests. */
class AuctionOwnerLifecycleTest {
    @Test void closeOnEdtReleasesTaskPrePinsWorkerAndExpressionsWhileKeepingTheNativeSystemReadable()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t2")) {
            AuctionTaskRuntimeTest.apply(f,f.frame("ENABLED"));var projector=f.fixture.projector;var owner=projector.coordinator().originalEngine();var system=projector.system();
            assertFalse(owner.admission().steps().active().isEmpty());assertTrue((int)owner.metrics().get("expressions")>=12);
            long began=System.nanoTime();SwingUtilities.invokeAndWait(projector::close);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)<750,"Closing a case owner cannot wait on I/O/worker on EDT");
            AuctionMonitorProjectionTest.await(()->Boolean.TRUE.equals(owner.metrics().get("terminated")));
            assertEquals(0,owner.metrics().get("expressions"));assertEquals(0,owner.metrics().get("pendingPins"));assertEquals(0L,owner.metrics().get("pinnedSerializedBytes"));assertEquals(0,owner.metrics().get("queue"));
            assertTrue(owner.admission().steps().active().isEmpty());assertTrue(owner.admission().steps().early().isEmpty());assertTrue(owner.admission().steps().latest().isEmpty());assertTrue(owner.admission().steps().seen().isEmpty());
            assertSame(system,projector.system());assertNotNull(system.model().getClass("Auction"));assertTrue(system.state().numObjects()>0,"Retiring verification cannot dispose the USE state");
        }
    }
    @Test void unchangedResyncPreservesOnlyContinuousHistoryAndGapCannotRecertifyPastTaskTruth()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t2")) {
            AuctionTaskRuntimeTest.apply(f,f.frame("ENABLED"));AuctionTaskRuntimeTest.start(f);AuctionTaskRuntimeTest.apply(f,f.frame("SUCCEEDED"));
            assertEquals("FULFILLED",AuctionTaskRuntimeTest.task(f).get("leafStatus"));var owner=f.fixture.projector.coordinator().originalEngine();var before=owner.checkpoint().get(5,TimeUnit.SECONDS);var system=f.fixture.projector.system();
            AuctionTaskRuntimeTest.resync(f);var after=owner.checkpoint().get(5,TimeUnit.SECONDS);assertEquals(before.histories(),after.histories(),"A repeated source cut cannot replay the historical recurrence");assertSame(system,f.fixture.projector.system());
            f.fixture.projector.coordinator().coverageGap("T02_EXPLICIT_LOST_SOURCE_INTERVAL");assertTrue(owner.admission().steps().active().isEmpty());assertTrue(owner.admission().steps().latest().isEmpty());
            AuctionTaskRuntimeTest.resync(f);var task=AuctionTaskRuntimeTest.task(f);assertEquals("HISTORY_GAP",task.get("quality"));
            assertNotEquals("KNOWN",task.get("quality"),"Current snapshot has no proof of the lost past interval");
        }
    }
    @Test void replacementEpochClearsObserverDemandTaskHistoryAndRejectsOldCallbacksOnTheSameSystem()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t2")) {
            AuctionTaskRuntimeTest.apply(f,f.frame("ENABLED"));var projector=f.fixture.projector;var owner=projector.coordinator().originalEngine();var system=projector.system();var old=f.fixture.run();
            assertFalse(owner.admission().steps().active().isEmpty());var now=Instant.now();String replacement=UUID.randomUUID().toString();
            // Explicit empty-new-epoch source fixture, not a fabricated live
            // producer restart. It exercises the existing authoritative reset.
            var snapshot=new RuntimeSnapshot("T02-new-epoch",old.modelRevision(),now,now,Map.of(),Map.of(),1,List.of(),Map.of("cartago",Completeness.COMPLETE),"explicit-empty-replacement-fixture");
            projector.applySnapshot(snapshot,replacement,old.generation()+1);AuctionTaskRuntimeTest.settled(f);
            assertSame(system,projector.system());assertEquals(replacement,owner.identity().run());assertTrue(owner.admission().steps().active().isEmpty());assertTrue(owner.admission().steps().seen().isEmpty());
            var image=owner.checkpoint().get(5,TimeUnit.SECONDS);assertTrue(image.taskRows().isEmpty());assertTrue(image.histories().isEmpty());
            assertTrue(system.state().allObjects().stream().noneMatch(o->Set.of("AuctionMonitor","AuctionObligation").contains(o.cls().name())));
            var late=f.frame("SUCCEEDED").event(old.id(),old.generation(),old.modelRevision(),100000,Instant.now(),"","");
            assertThrows(NativeRuntimeProtocolException.class,()->projector.apply(late));assertTrue(owner.admission().steps().active().isEmpty());
        }
    }
}
