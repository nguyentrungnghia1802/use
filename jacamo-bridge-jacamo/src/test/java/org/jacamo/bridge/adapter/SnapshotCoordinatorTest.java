package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ContractException;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;
import org.junit.jupiter.api.Test;

class SnapshotCoordinatorTest {
    @Test void retriesConcurrentTopologyChangeAndReplaysOnlyAfterAcceptedWatermark() throws Exception {
        var source=new FakeSource(false,true); try(var coordinator=new SnapshotCoordinator(List.of(source),8)){
            var capture=coordinator.capture("model-1",3);
            assertEquals(2,capture.snapshot().validationAttempts());
            assertEquals(1,capture.snapshot().facts().size());
            assertEquals(1,capture.replay().size());
            assertTrue(capture.replay().getFirst().sourceSequence()>capture.snapshot().endWatermarks().get(source.sourceId()).sequence());
        }
        assertTrue(source.closed.get());
    }

    @Test void overflowNeverSilentlyPublishesSnapshot() throws Exception {
        var source=new FakeSource(true,false);var stream=new java.util.ArrayList<RuntimeEvent>();
        try(var coordinator=new SnapshotCoordinator(List.of(source),1,stream::add)){
            assertThrows(ContractException.class,()->coordinator.capture("model-1",2));
            assertTrue(stream.stream().anyMatch(e->e.kind()==RuntimeEventKind.GAP
                    && e.after().get("diagnostic").equals("SNAPSHOT_CALLBACK_BUFFER_OVERFLOW")));
        }
    }

    @Test void olderLiveSubscriberReceivesEveryCallbackAcrossRejectedAndAcceptedCuts() throws Exception {
        var source=new FakeSource(false,true);var stream=new java.util.ArrayList<RuntimeEvent>();
        try(var coordinator=new SnapshotCoordinator(List.of(source),8,stream::add)) {
            var capture=coordinator.capture("model-1",3);
            assertEquals(List.of(1L,2L),stream.stream().map(RuntimeEvent::sourceSequence).toList());
            assertEquals(List.of(2L),capture.replay().stream().map(RuntimeEvent::sourceSequence).toList());
            assertEquals(1L,capture.snapshot().endWatermarks().get("fake").sequence());
        }
    }

    @Test void sameNameRecreationHasDifferentIncarnationIdentity() {
        var one=new BridgeEntityId("cartago","environment","artifact","w","a","uuid-1");
        var two=new BridgeEntityId("cartago","environment","artifact","w","a","uuid-2");
        assertNotEquals(one,two); assertNotEquals(one.canonical(),two.canonical());
    }
    @Test void sameTopologyWithMutationDuringCaptureIsNotAnAuthoritativeCut() throws Exception {
        try (var coordinator = new SnapshotCoordinator(List.of(new FakeSource(false, false)), 8)) {
            assertThrows(ContractException.class, () -> coordinator.capture("model-1", 2));
        }
    }
    @Test void explicitBoundaryHasCorrelationAndIsIncludedInAuthoritativeWatermarks() throws Exception {
        var source=new BridgeCheckpointSource();var events=new java.util.ArrayList<RuntimeEvent>();
        try(var coordinator=new SnapshotCoordinator(List.of(source),8,events::add)) {
            var cause=new FakeSource(false,false).event(4);source.boundary(cause);
            assertEquals(1,events.size());var boundary=events.getFirst();
            assertEquals(RuntimeEventKind.STREAM_BOUNDARY,boundary.kind());assertEquals(cause.eventId(),boundary.causationId());
            assertEquals(cause.eventId(),boundary.correlationId());assertEquals(4L,((Number)boundary.after().get("boundarySourceSequence")).longValue());
            var cut=coordinator.capture("model-1",1).snapshot();
            assertEquals(1L,cut.endWatermarks().get(source.sourceId()).sequence());
            assertEquals(Completeness.COMPLETE,cut.sourceCompleteness().get(source.sourceId()));
            source.boundary(cause);assertEquals(2L,events.getLast().sourceSequence());
        }
    }

    private static final class FakeSource implements SnapshotSource {
        private final boolean overflow;private final boolean mutateOnce;private final AtomicLong sequence=new AtomicLong();private final AtomicBoolean closed=new AtomicBoolean();private Consumer<RuntimeEvent> observer;private int topologyCalls;private int captures;
        FakeSource(boolean overflow,boolean mutateOnce){this.overflow=overflow;this.mutateOnce=mutateOnce;}
        @Override public String sourceId(){return "fake";}
        @Override public void attach(Consumer<RuntimeEvent> observer){this.observer=observer;}
        @Override public SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence.get());}
        @Override public String topologyFingerprint(){topologyCalls++;if(mutateOnce&&topologyCalls==2)return "changed";if(mutateOnce&&topologyCalls==4){long seq=sequence.incrementAndGet();observer.accept(event(seq));}return "stable";}
        @Override public List<RuntimeFact> capture(){int count=overflow?3:mutateOnce&&captures++>0?0:1;for(int i=0;i<count;i++){long seq=sequence.incrementAndGet();observer.accept(event(seq));}return List.of(new RuntimeFact(id(),RuntimeFactKind.AGENT,Map.of(),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));}
        @Override public Completeness completeness(){return Completeness.COMPLETE;}
        @Override public void close(){closed.set(true);}
        private BridgeEntityId id(){return new BridgeEntityId("fake","agent","runtime","p","a","i");}
        private RuntimeEvent event(long seq){return new RuntimeEvent("e"+seq,"s",1,"model-1","fake",sourceId(),seq,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,id(),null,"","",Map.of(),Map.of(),new SourceWatermark(sourceId(),seq),Completeness.COMPLETE,List.of());}
    }
}
