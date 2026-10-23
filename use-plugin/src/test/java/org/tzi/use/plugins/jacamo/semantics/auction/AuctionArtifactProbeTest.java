package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

class AuctionArtifactProbeTest {
    @Test void actualPrivateCandidateIsDistinctFromPublishedWinnerUntilStop()throws Exception {
        try(var fixture=new AuctionLiveFixture(true)) {
            var id=fixture.auction("probe"+UUID.randomUUID().toString().replace("-",""));
            var initial=state(fixture,id);
            assertEquals("no_winner",CanonicalJson.object(CanonicalJson.object(initial.get("fields")).get("currentWinner")).get("value"));
            fixture.actor.doAction(id,new Op("start","service"),5000);fixture.actor.doAction(id,new Op("bid",42.5),5000);
            var bid=state(fixture,id);assertEquals(fixture.actor.getName(),CanonicalJson.object(CanonicalJson.object(bid.get("fields")).get("currentWinner")).get("value"));
            var actual=fixture.environment.resolveWSP(fixture.workspace.getFullName()).getWorkspace().getArtifactDescriptor(id.getName()).getArtifact();
            var field=actual.getClass().getDeclaredField("currentWinner");assertTrue(field.trySetAccessible());assertEquals(field.get(actual),CanonicalJson.object(CanonicalJson.object(bid.get("fields")).get("currentWinner")).get("value"));
            assertEquals("no_winner",property(fixture,id,"winner").toString());
            fixture.actor.doAction(id,new Op("bid",100.0),5000);assertEquals(CanonicalJson.object(bid.get("fields")),CanonicalJson.object(state(fixture,id).get("fields")));
            fixture.actor.doAction(id,new Op("stop"),5000);assertEquals(fixture.actor.getName(),property(fixture,id,"winner").toString());
            assertEquals("COMPLETE",state(fixture,id).get("status"));
        }
    }
    @Test void twoActualPerformersKeepNonImprovingSuccessSeparateFromWinningBid()throws Exception {
        try(var fixture=new AuctionLiveFixture(true)) {
            var id=fixture.auction("bidders");var second=new CartagoBasicContext("otherBidder");
            try {
                fixture.actor.doAction(id,new Op("start","flight"),5000);
                fixture.actor.doAction(id,new Op("bid",42.5),5000);
                second.doAction(id,new Op("bid",100.0),5000);
                var unchanged=state(fixture,id);assertEquals(fixture.actor.getName(),candidate(unchanged));assertEquals("42.5",scalar(unchanged,"best_bid").get("value"));
                var nonImproving=fixture.events.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"bid".equals(e.after().get("operation"))
                        &&((List<?>)e.after().get("argumentValues")).equals(List.of("100.0"))).findFirst().orElseThrow();
                assertTrue(((String)nonImproving.after().get("performerSemanticId")).contains(second.getName()));
                assertEquals(fixture.actor.getName(),candidate(CanonicalJson.object(nonImproving.after().get("auctionStateObservation"))));
                second.doAction(id,new Op("bid",10.0),5000);assertEquals(second.getName(),candidate(state(fixture,id)));
                assertEquals("no_winner",scalar(state(fixture,id),"winner").get("atomName"));
                fixture.actor.doAction(id,new Op("stop"),5000);assertEquals(second.getName(),scalar(state(fixture,id),"winner").get("atomName"));
                var before=state(fixture,id);
                assertThrows(cartago.util.agent.ActionFailedException.class,()->second.doAction(id,new Op("bid",1.0),5000));
                assertEquals(before,state(fixture,id));
            }finally{quit(fixture,second);}
        }
    }
    @Test void noBidsDoesNotInventAPlayerAndStartDoesNotResetTheActualArtifact()throws Exception {
        try(var fixture=new AuctionLiveFixture(true)) {
            var id=fixture.auction("noBids");var initial=state(fixture,id);
            assertEquals("7fefffffffffffff",scalar(initial,"best_bid").get("rawBits"));assertEquals(Double.toString(Double.MAX_VALUE),scalar(initial,"best_bid").get("value"));
            fixture.actor.doAction(id,new Op("start","flight"),5000);fixture.actor.doAction(id,new Op("stop"),5000);
            assertEquals("no_winner",candidate(state(fixture,id)));assertEquals("no_winner",scalar(state(fixture,id),"winner").get("atomName"));
            fixture.actor.doAction(id,new Op("start","service \"quoted\" \\ path"),5000);fixture.actor.doAction(id,new Op("bid",2.0),5000);fixture.actor.doAction(id,new Op("stop"),5000);
            fixture.actor.doAction(id,new Op("start","new service"),5000);
            assertEquals("2.0",scalar(state(fixture,id),"best_bid").get("value"));assertEquals(fixture.actor.getName(),candidate(state(fixture,id)));
            assertEquals(fixture.actor.getName(),scalar(state(fixture,id),"winner").get("atomName"));
        }
    }
    @Test void duplicateNamesRecreationAndClosedProbeCannotReuseAnOldCandidate()throws Exception {
        try(var fixture=new AuctionLiveFixture(true);var probe=new AuctionArtifactStateProbe(fixture.environment,fixture.source)) {
            var first=fixture.auction("same");var other=fixture.auction("same",fixture.otherWorkspace());
            fixture.actor.doAction(first,new Op("start","one"),5000);fixture.actor.doAction(first,new Op("bid",3.0),5000);
            assertEquals(fixture.actor.getName(),candidate(probe.capture(first,"TEST","")));assertEquals("no_winner",candidate(probe.capture(other,"TEST","")));
            fixture.dispose(first);var fresh=fixture.auction("same");assertNotEquals(first.getId(),fresh.getId());
            var retired=probe.capture(first,"TEST","");assertEquals("INCOMPLETE",retired.get("status"));assertFalse(retired.containsKey("fields"));
            assertEquals("no_winner",candidate(probe.capture(fresh,"TEST","")));probe.close();
            assertEquals("PROBE_CLOSED",probe.capture(fresh,"TEST","").get("diagnostic"));
        }
    }
    @Test void accessDeniedOrUnapprovedProviderNeverSuppliesAFieldDefault()throws Exception {
        try(var fixture=new AuctionLiveFixture(true)) {
            var id=fixture.auction("denied");var checked=new ArrayList<String>();
            try(var probe=new AuctionArtifactStateProbe(fixture.environment,fixture.source,field->{checked.add(field.getDeclaringClass().getName()+"."+field.getName());return false;})) {
                assertFalse(probe.available());var missing=probe.capture(id,"TEST","");assertEquals("INCOMPLETE",missing.get("status"));assertFalse(missing.containsKey("fields"));
                assertTrue(probe.diagnostic().contains("FIXED_MEMBER_ACCESS_DENIED"));assertEquals(List.of("auction_env.AuctionArtifact.currentWinner"),checked);
            }
            for(String key:List.of("sourceDigest","sourceUri","provider")) {
                var tree=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(ContractPayloads.model(fixture.source)),Map.class);
                corruptReference(tree,key);
                try(var probe=new AuctionArtifactStateProbe(fixture.environment,ContractPayloads.model(tree))) {
                    assertFalse(probe.available(),key);assertFalse(probe.capture(id,"TEST","").containsKey("fields"),key);
                }
            }
        }
    }
    @Test @Timeout(30) void busyArtifactFailsImmediatelyAndCallbackReentrancyRestoresTheLock()throws Exception {
        try(var fixture=new AuctionLiveFixture(true);var probe=new AuctionArtifactStateProbe(fixture.environment,fixture.source)) {
            var id=fixture.auction("locked");var actual=fixture.environment.resolveWSP("/main").getWorkspace().getArtifactDescriptor(id.getName()).getArtifact();
            var member=Artifact.class.getDeclaredField("lock");assertTrue(member.trySetAccessible());var lock=(ReentrantLock)member.get(actual);
            var held=new CountDownLatch(1);var release=new CountDownLatch(1);var worker=Executors.newSingleThreadExecutor();
            try {
                var owner=worker.submit(()->{lock.lock();try{held.countDown();release.await(10,TimeUnit.SECONDS);}finally{lock.unlock();}return null;});
                assertTrue(held.await(5,TimeUnit.SECONDS));long start=System.nanoTime();var unavailable=probe.capture(id,"TEST","");
                assertEquals("INCOMPLETE",unavailable.get("status"));assertTrue(((String)unavailable.get("diagnostic")).contains("ARTIFACT_BUSY"));
                assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(2));assertFalse(unavailable.containsKey("fields"));release.countDown();owner.get(5,TimeUnit.SECONDS);
                lock.lock();try{int count=lock.getHoldCount();assertEquals("COMPLETE",probe.capture(id,"TEST_REENTRANT","").get("status"));assertEquals(count,lock.getHoldCount());}finally{lock.unlock();}
                fixture.actor.doAction(id,new Op("start","flight"),5000);fixture.actor.doAction(id,new Op("bid",5.0),5000);
                var callbacks=fixture.events.stream().filter(e->id.getId().toString().equals(e.entityId().incarnation())&&e.after().get("auctionStateObservation") instanceof Map<?,?>&&e.after().containsKey("opId")).toList();
                assertFalse(callbacks.isEmpty());
                for(var event:callbacks) {
                    var cut=CanonicalJson.object(event.after().get("auctionStateObservation"));assertEquals("COMPLETE",cut.get("status"),cut.toString());
                    var proof=CanonicalJson.object(cut.get("probeEvidence"));assertEquals(true,proof.get("lockAlreadyHeld"));assertEquals(event.after().get("opId"),proof.get("invocation"));
                }
                assertFalse(lock.isLocked());
            }finally{release.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));}
        }
    }
    @Test @Timeout(45) void concurrentRealOperationsAndSnapshotsDoNotDeadlockOrEmitProbeSideEffects()throws Exception {
        try(var fixture=new AuctionLiveFixture(true);var probe=new AuctionArtifactStateProbe(fixture.environment,fixture.source)) {
            var id=fixture.auction("concurrent");fixture.actor.doAction(id,new Op("start","flight"),5000);
            var worker=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
            try {
                var ops=worker.submit(()->{gate.await();for(int i=30;i>0;i--)fixture.actor.doAction(id,new Op("bid",(double)i),5000);return null;});
                var reads=worker.submit(()->{gate.await();int complete=0;for(int i=0;i<60;i++) {
                    fixture.cartago.capture();var cut=probe.capture(id,"TEST","");
                    if("COMPLETE".equals(cut.get("status"))) {
                        complete++;var properties=CanonicalJson.object(cut.get("properties"));assertEquals(Set.of("running","task","best_bid","winner"),properties.keySet());
                        assertEquals("no_winner",scalar(cut,"winner").get("atomName"));
                        if(!Double.toString(Double.MAX_VALUE).equals(scalar(cut,"best_bid").get("value")))assertEquals(fixture.actor.getName(),candidate(cut));
                    }else{assertEquals("INCOMPLETE",cut.get("status"));assertFalse(cut.containsKey("fields"));}
                }return complete;});
                gate.countDown();ops.get(25,TimeUnit.SECONDS);assertTrue(reads.get(25,TimeUnit.SECONDS)>0);
                var finalCut=state(fixture,id);assertEquals("1.0",scalar(finalCut,"best_bid").get("value"));
                var mark=fixture.cartago.watermark();int events=fixture.events.size();
                for(int i=0;i<4;i++)assertEquals(finalCut,state(fixture,id));
                assertEquals(mark,fixture.cartago.watermark());assertEquals(events,fixture.events.size());
                assertEquals("1.0",scalar(finalCut,"best_bid").get("value"));assertEquals("no_winner",scalar(finalCut,"winner").get("atomName"));
            }finally{gate.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));}
        }
    }
    @Test void actualFieldCutsUseTheExistingVerificationLaneWithoutPromotingClockOrPopulation()throws Exception {
        try(var fixture=new AuctionLiveFixture(true);var observer=new VerificationObservationSource(fixture.run.id(),fixture.run.generation(),fixture.run.modelRevision())) {
            var events=new CopyOnWriteArrayList<RuntimeEvent>();observer.attach(events::add);
            var id=fixture.auction("wire");fixture.actor.doAction(id,new Op("start","flight"),5000);fixture.actor.doAction(id,new Op("bid",8.0),5000);fixture.actor.doAction(id,new Op("stop"),5000);
            assertTrue(events.size()>=7,events.toString());assertEquals(Completeness.PARTIAL,observer.completeness());
            for(var event:events) {
                var frame=VerificationObservation.require(event);assertEquals(VerificationObservation.Kind.ARTIFACT_FIELD_CUT,frame.kind());assertEquals(Completeness.COMPLETE,frame.quality());
                assertEquals("UNAVAILABLE",frame.sourceTime().status());assertTrue(frame.subject().contains(id.getId().toString()));
                var parent=fixture.events.stream().filter(e->e.eventId().equals(event.causationId())).findFirst().orElseThrow();
                assertEquals(CanonicalJson.object(parent.after().get("auctionStateObservation")).get("fields"),frame.data().get("fields"));
                assertEquals(parent.after().getOrDefault("opId",""),CanonicalJson.object(frame.data().get("probeEvidence")).get("invocation"));
            }
            assertEquals(fixture.actor.getName(),CanonicalJson.object(CanonicalJson.object(VerificationObservation.require(events.getLast()).data().get("fields")).get("currentWinner")).get("value"));
        }
    }
    static String candidate(Map<String,Object> state){return (String)CanonicalJson.object(CanonicalJson.object(state.get("fields")).get("currentWinner")).get("value");}
    static Map<String,Object> scalar(Map<String,Object> state,String name){return CanonicalJson.object(CanonicalJson.object(state.get("properties")).get(name));}
    static void quit(AuctionLiveFixture fixture,CartagoBasicContext actor)throws Exception {
        CslRuntimeTestRegistry.closeCartagoContext(actor);
    }
    @SuppressWarnings("unchecked") static void corruptReference(Object tree,String key) {
        if(tree instanceof List<?> list){list.forEach(item->corruptReference(item,key));return;}
        if(tree instanceof Map<?,?> raw) {
            var map=(Map<String,Object>)raw;
            if(map.get("diagnostics") instanceof List<?> diagnostics&&diagnostics.contains("SOURCE_ACTION_CARTAGO_ARTIFACT_APPLICATION_CLASS")) {
                if(key.equals("provider"))map.put("diagnostics",diagnostics.stream().map(d->d instanceof String s&&s.startsWith("PROVIDER:")?"PROVIDER:file:/wrong/":d).toList());else map.put(key,key.equals("sourceDigest")?"0".repeat(64):"file:/wrong/AuctionArtifact.class");
            }
            new ArrayList<>(map.values()).forEach(item->corruptReference(item,key));
        }
    }
    static Map<String,Object> state(AuctionLiveFixture fixture,ArtifactId id) {
        var artifact=fixture.cartago.capture().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&id.getId().toString().equals(f.values().get("uuid"))).findFirst().orElseThrow();
        var value=artifact.values().get("auctionStateObservation");assertNotNull(value,"A real source field cut is required; four observables do not reveal currentWinner");
        return CanonicalJson.object(value);
    }
    static Object property(AuctionLiveFixture fixture,ArtifactId id,String name)throws Exception {
        var info=fixture.environment.getController(id.getWorkspaceId().getFullName()).getArtifactInfo(id.getName());assertEquals(id,info.getId());
        return info.getObsProperties().stream().filter(p->p.getName().equals(name)).findFirst().orElseThrow().getValue();
    }
}
