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
        var source=new FakeSource(true,false); try(var coordinator=new SnapshotCoordinator(List.of(source),1)){
            assertThrows(ContractException.class,()->coordinator.capture("model-1",2));
        }
    }

    @Test void sameNameRecreationHasDifferentIncarnationIdentity() {
        var one=new BridgeEntityId("cartago","environment","artifact","w","a","uuid-1");
        var two=new BridgeEntityId("cartago","environment","artifact","w","a","uuid-2");
        assertNotEquals(one,two); assertNotEquals(one.canonical(),two.canonical());
    }

    private static final class FakeSource implements SnapshotSource {
        private final boolean overflow;private final boolean mutateOnce;private final AtomicLong sequence=new AtomicLong();private final AtomicBoolean closed=new AtomicBoolean();private Consumer<RuntimeEvent> observer;private int topologyCalls;
        FakeSource(boolean overflow,boolean mutateOnce){this.overflow=overflow;this.mutateOnce=mutateOnce;}
        @Override public String sourceId(){return "fake";}
        @Override public void attach(Consumer<RuntimeEvent> observer){this.observer=observer;}
        @Override public SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence.get());}
        @Override public String topologyFingerprint(){topologyCalls++;if(mutateOnce&&topologyCalls==2)return "changed";if(mutateOnce&&topologyCalls==4){long seq=sequence.incrementAndGet();observer.accept(event(seq));}return "stable";}
        @Override public List<RuntimeFact> capture(){int count=overflow?3:1;for(int i=0;i<count;i++){long seq=sequence.incrementAndGet();observer.accept(event(seq));}return List.of(new RuntimeFact(id(),RuntimeFactKind.AGENT,Map.of(),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));}
        @Override public Completeness completeness(){return Completeness.COMPLETE;}
        @Override public void close(){closed.set(true);}
        private BridgeEntityId id(){return new BridgeEntityId("fake","agent","runtime","p","a","i");}
        private RuntimeEvent event(long seq){return new RuntimeEvent("e"+seq,"s",1,"model-1","fake",sourceId(),seq,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,id(),null,"","",Map.of(),Map.of(),new SourceWatermark(sourceId(),seq),Completeness.COMPLETE,List.of());}
    }
}
