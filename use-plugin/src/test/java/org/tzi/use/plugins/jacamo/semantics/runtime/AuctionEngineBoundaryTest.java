package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.auction.AuctionCastFixture;
import static org.junit.jupiter.api.Assertions.*;

class AuctionEngineBoundaryTest {
    @Test void failedMembershipExpansionRestoresThePreviousRevisionWithoutRewindingCommittedNativeFacts()throws Exception {
        try(var fixture=new AuctionCastFixture(2,1)) {
            var coordinator=fixture.projector().coordinator();coordinator.awaitEvaluation(coordinator.latest(),15000);var owner=coordinator.originalEngine();var before=owner.checkpoint().get(10,TimeUnit.SECONDS);var builds=owner.metrics().get("topologyBuilds");
            owner.faultProbe(point->{if(point.equals("AFTER_TOPOLOGY_BUILD"))throw new IllegalStateException("AUCTION_NEW_MEMBERSHIP_TOPOLOGY");});
            fixture.commitParticipant(1,0);fixture.resync();coordinator.originalEngineDrained().get(10,TimeUnit.SECONDS);coordinator.read(()->null);
            assertEquals(before.histories(),owner.checkpoint().get(10,TimeUnit.SECONDS).histories());assertEquals(builds,owner.metrics().get("topologyBuilds"));
            assertEquals("STALE",coordinator.latest().freshness());assertTrue(coordinator.latest().diagnostic().contains("AUCTION_NEW_MEMBERSHIP_TOPOLOGY"));
            assertTrue(fixture.projector().system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionMonitor")).anyMatch(o->o.state(fixture.projector().system().state()).attributeValue("bidCommittedBy") instanceof org.tzi.use.uml.ocl.value.SetValue set&&set.collection().size()==2));
        }
    }
    @Test void nativeOclReadsThePinnedAuctionCutAfterTheLiveStateHasAdvanced()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector();var coordinator=projector.coordinator();var accepted=coordinator.awaitEvaluation(coordinator.latest(),15000);var owner=coordinator.originalEngine();
            var pinned=coordinator.read(()->owner.prepare(accepted.stateVersion(),accepted.semantic().boundary(),accepted.stateHash(),Map.of("cartago",org.jacamo.bridge.contract.Completeness.COMPLETE),false,false,List.of(),new org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.PreparedEvaluation(projector.system().model(),List.of()),4096));
            var self=projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")&&projector.mutations().metadata(o,"name").equals("castOne")).findFirst().orElseThrow();
            fixture.startAndEnableBid(0);fixture.resync();coordinator.awaitEvaluation(coordinator.latest(),15000);
            assertSame(projector.system(),pinned.state().after().system());assertNotSame(projector.system().state(),pinned.state().after());
            try(var ocl=new org.tzi.use.semantic.engine.use75.NativeOclBackend(projector.system().model(),fixture.program().hash(),"U02_PINNED_TEST")) {
                var expression=ocl.compile(new org.tzi.use.semantic.engine.use75.OclContractSpec("U02-Idle",org.tzi.use.semantic.engine.use75.OclContractSpec.Kind.INVARIANT,self.cls(),null,"not self.running"));
                assertEquals("PASS",ocl.evaluate(expression,pinned.state(),Map.of("self",self.value()),"").status().name());
                assertEquals("FAIL",ocl.evaluate(expression,org.tzi.use.semantic.engine.use75.UseStateReadView.pin(null,projector.system().state()),Map.of("self",self.value()),"").status().name());
            }
            String actualHash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().exportState(pinned.state().after()).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(accepted.stateHash(),actualHash);
        }
    }
    @Test void failedPropagationRetainsNativeSourceTruthAndRestoresAllOriginalHistories()throws Exception {
        for(String point:List.of("BEFORE_ENGINE_STEP","AFTER_PROPAGATION"))try(var fixture=new AuctionCastFixture(1)) {
            var coordinator=fixture.projector().coordinator();coordinator.awaitEvaluation(coordinator.latest(),15000);var owner=coordinator.originalEngine();var before=owner.checkpoint().get(10,TimeUnit.SECONDS);
            owner.faultProbe(stage->{if(stage.equals(point))throw new IllegalStateException(stage);});
            fixture.startAndEnableBid(0);fixture.resync();coordinator.originalEngineDrained().get(10,TimeUnit.SECONDS);coordinator.read(()->null);
            assertEquals(before.histories(),owner.checkpoint().get(10,TimeUnit.SECONDS).histories());
            assertTrue(fixture.projector().system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).anyMatch(o->o.state(fixture.projector().system().state()).attributeValue("running").toString().equals("true")));
            assertEquals("STALE",coordinator.latest().freshness());assertTrue(coordinator.latest().diagnostic().contains(point));assertEquals(0,owner.metrics().get("pendingPins"));
        }
    }
    @Test void slowOldCutIsPinnedAndCloseFencesItsResultWithoutBlockingEdt()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var coordinator=fixture.projector().coordinator();coordinator.awaitEvaluation(coordinator.latest(),15000);var owner=coordinator.originalEngine();
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            owner.faultProbe(point->{if(point.equals("BEFORE_ENGINE_STEP")){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("SLOW_TEST_TIMEOUT");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new CancellationException("OWNER_CLOSED");}}});
            fixture.startAndEnableBid(0);fixture.resync();assertTrue(entered.await(5,TimeUnit.SECONDS));var accepted=coordinator.latest();
            fixture.startAndEnableBid(1);fixture.resync();var newer=coordinator.latest();assertNotEquals(accepted.stateHash(),newer.stateHash());
            long start=System.nanoTime();javax.swing.SwingUtilities.invokeAndWait(owner::close);assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000);release.countDown();
            coordinator.read(()->null);assertEquals(0,owner.metrics().get("pendingPins"));assertEquals(true,owner.metrics().get("closed"));
            assertFalse(coordinator.history().stream().filter(r->r.semantic()!=null&&r.semantic().boundary().equals(accepted.semantic().boundary())).map(r->CanonicalJson.object(r.semantic().observation().get("originalEngine"))).anyMatch(c->c.get("evaluated").equals("PINNED_NATIVE_CUT")),"Closed owner cannot publish the blocked old cut");
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!Boolean.TRUE.equals(owner.metrics().get("terminated"))&&System.nanoTime()<end)Thread.sleep(10);assertEquals(true,owner.metrics().get("terminated"));
        }
    }
}
