package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.*;

/** Explicit callback batch boundaries participate in the same watermarked snapshot cut. */
final class BridgeCheckpointSource implements SnapshotSource {
    private long sequence;
    private Consumer<RuntimeEvent> observer=event->{ };
    private boolean closed;
    @Override public String sourceId(){return VerificationBoundary.SOURCE_ID;}
    @Override public synchronized void attach(Consumer<RuntimeEvent> observer){this.observer=observer;}
    @Override public synchronized SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence);}
    @Override public String topologyFingerprint(){return "callback-boundary-v1";}
    @Override public List<RuntimeFact> capture(){return List.of();}
    @Override public Completeness completeness(){return Completeness.COMPLETE;}
    void boundary(RuntimeEvent cause) {
        RuntimeEvent event;Consumer<RuntimeEvent> callback;
        synchronized(this) {
            if(closed)return;
            long next=++sequence;callback=observer;
            event=new RuntimeEvent("boundary:"+cause.sessionId()+":"+next,cause.sessionId(),cause.generation(),cause.modelRevision(),
                    "bridge",sourceId(),next,Instant.now(),RuntimeEventKind.STREAM_BOUNDARY,RuntimeFactKind.RUNTIME_EVENT,
                    ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("bridge","runtime","verification-boundary",cause.sessionId(),"batch",cause.eventId()),
                    null,cause.correlationId().isBlank()?cause.eventId():cause.correlationId(),cause.eventId(),Map.of(),
                    Map.of("boundarySource",cause.sourceId(),"boundaryEventId",cause.eventId(),"boundarySourceSequence",cause.sourceSequence()),
                    new SourceWatermark(sourceId(),next),Completeness.COMPLETE,List.of());
        }
        callback.accept(event);
    }
    @Override public synchronized void close(){closed=true;observer=event->{ };}
}
