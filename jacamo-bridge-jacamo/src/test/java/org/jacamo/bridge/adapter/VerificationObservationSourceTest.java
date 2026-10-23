package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerificationObservationSourceTest {
    static VerificationObservation clock(String subject) {
        return new VerificationObservation(VerificationObservation.Kind.CLOCK,subject,Completeness.PARTIAL,"TEST_CLOCK_UNAVAILABLE",
                VerificationObservation.SourceTime.unavailable("TEST_ONLY_NO_SOURCE_CLOCK"),Map.of("clockIdentity",subject,"sourceProof",Map.of("origin","TEST_ONLY")));
    }
    @Test void callbacksCannotOvertakeTheSourceSequenceAcrossProducerThreads()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var secondStarted=new CountDownLatch(1);var delivered=new CopyOnWriteArrayList<Long>();
        try(var source=new VerificationObservationSource("run",1,"revision")) {
            source.attach(event->{if(event.sourceSequence()==1){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("TEST_RELEASE_TIMEOUT");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}delivered.add(event.sourceSequence());});
            var executor=Executors.newFixedThreadPool(2);
            try {
                var first=executor.submit(()->source.publish(clock("clock"),"",""));assertTrue(entered.await(3,TimeUnit.SECONDS));
                var second=executor.submit(()->{secondStarted.countDown();source.publish(clock("clock"),"","");});assertTrue(secondStarted.await(3,TimeUnit.SECONDS));
                assertThrows(TimeoutException.class,()->second.get(150,TimeUnit.MILLISECONDS),"Second callback passed an earlier uncompleted publication");
                release.countDown();first.get(3,TimeUnit.SECONDS);second.get(3,TimeUnit.SECONDS);assertEquals(List.of(1L,2L),delivered);
            }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
        }
    }
    @Test void snapshotsAreBoundedScopedPartialAndOverflowIsAnExplicitGap()throws Exception {
        var events=new ArrayList<RuntimeEvent>();
        try(var source=new VerificationObservationSource("run",7,"revision",1);var coordinator=new SnapshotCoordinator(List.of(source),2,events::add)) {
            source.publish(clock("one"),"parent","parent");
            var cut=coordinator.capture("revision",2).snapshot();assertEquals(1,cut.facts().size());assertEquals(Completeness.PARTIAL,cut.sourceCompleteness().get(source.sourceId()));
            assertEquals(clock("one"),VerificationObservation.require(cut.facts().getFirst(),cut,"run",7));
            source.publish(clock("two"),"parent","parent");assertEquals(RuntimeEventKind.GAP,events.getLast().kind());assertEquals(2,source.watermark().sequence());
            assertEquals(1,source.capture().size());assertEquals(true,source.captureMetadata().get("overflow"));
        }
    }
    @Test void oldRunCallbacksAndClosedSourcesCannotPublishIntoANewOwner()throws Exception {
        var events=new ArrayList<RuntimeEvent>();var old=new VerificationObservationSource("old-run",1,"revision");old.attach(event->{});old.close();
        try(var source=new VerificationObservationSource("new-run",2,"revision")) {
            source.attach(events::add);old.publish(clock("old"),"","");
            var parent=clock("old").event("old-run",1,"revision",1,Instant.EPOCH,"","");
            VerificationObservationSource.publishIfAttached(parent,()->{fail("Old source executed a new-run capture");return clock("old");});
            assertTrue(events.isEmpty());assertEquals(0,source.watermark().sequence());
            var valid=clock("new").event("new-run",2,"revision",1,Instant.EPOCH,"","");
            VerificationObservationSource.publishIfAttached(valid,()->clock("new"));assertEquals(1,events.size());
            VerificationObservationSource.publishIfAttached(valid,()->{throw new IllegalArgumentException("unavailable source field");});
            assertEquals(RuntimeEventKind.GAP,events.getLast().kind());assertEquals(Completeness.PARTIAL,source.completeness());
        }
    }
}
