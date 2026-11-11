package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.*;

/** One run-owned, bounded observer publication lane on the existing bridge.
 * There is no timer, network transport, framework control or USE state here. */
public final class VerificationObservationSource implements SnapshotSource {
    private static final AtomicReference<VerificationObservationSource> ACTIVE=new AtomicReference<>();
    private final String run,revision;private final long generation;private final int capacity;
    private final Map<String,RuntimeFact> latest=new LinkedHashMap<>();
    private final Object publicationLock=new Object();
    private Consumer<RuntimeEvent> observer;private long sequence;private boolean closed,overflow;
    private String failure="";
    public VerificationObservationSource(String run,long generation,String revision){this(run,generation,revision,4096);}
    public VerificationObservationSource(String run,long generation,String revision,int capacity) {
        if(run==null||run.isBlank()||revision==null||revision.isBlank()||generation<0||capacity<1||capacity>16384)throw new IllegalArgumentException("VERIFICATION_SOURCE_CONFIGURATION");
        this.run=run;this.generation=generation;this.revision=revision;this.capacity=capacity;
    }
    @Override public String sourceId(){return VerificationObservation.SOURCE_ID;}
    @Override public synchronized void attach(Consumer<RuntimeEvent> observer) {
        Objects.requireNonNull(observer);
        if(closed||this.observer!=null||!ACTIVE.compareAndSet(null,this))throw new IllegalStateException("VERIFICATION_SOURCE_ALREADY_ATTACHED_OR_CLOSED");this.observer=observer;
    }
    public void publish(VerificationObservation observation,String correlation,String cause) {
        synchronized(publicationLock) {
        RuntimeEvent event;Consumer<RuntimeEvent> callback;
        synchronized(this) {
            if(closed)return;if(observer==null)throw new IllegalStateException("VERIFICATION_SOURCE_NOT_ATTACHED");
            event=observation.event(run,generation,revision,sequence+1,Instant.now(),correlation,cause);String observedKey=event.entityId().canonical();
            event=observation.event(run,generation,revision,++sequence,event.observedAt(),correlation,cause,!latest.containsKey(observedKey));VerificationObservation.require(event);callback=observer;
            String key=event.entityId().canonical();
            if(!latest.containsKey(key)&&latest.size()>=capacity) {
                overflow=true;event=new RuntimeEvent(event.eventId()+":gap",run,generation,revision,"observer",sourceId(),sequence,event.observedAt(),RuntimeEventKind.GAP,null,null,null,null,correlation,cause,Map.of(),
                        Map.of("diagnostic","VERIFICATION_OBSERVATION_CAPACITY_EXCEEDED"),event.watermark(),Completeness.PARTIAL,List.of());
            }else latest.put(key,new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),ProjectionStatus.EVIDENCE_ONLY,event.completeness(),event.evidence()));
        }
        callback.accept(event);
        }
    }
    /** Used only by producer hooks; no externally supplied XML or path is consulted. */
    static void publishIfAttached(RuntimeEvent parent,java.util.function.Supplier<VerificationObservation> observation) {
        var source=ACTIVE.get();
        if(source==null||!source.run.equals(parent.sessionId())||source.generation!=parent.generation()||!source.revision.equals(parent.modelRevision()))return;
        try {source.publish(observation.get(),parent.eventId(),parent.eventId());}
        catch(RuntimeException unavailable){source.reportGap(parent,"VERIFICATION_OBSERVATION_CAPTURE_FAILED:"+unavailable.getClass().getSimpleName());}
    }
    private void reportGap(RuntimeEvent parent,String diagnostic) {
        synchronized(publicationLock) {
            RuntimeEvent event;Consumer<RuntimeEvent> callback;
            synchronized(this) {
                if(closed||observer==null)return;failure=diagnostic;long next=++sequence;callback=observer;
                event=new RuntimeEvent(run+":verification-gap:"+next,run,generation,revision,"observer",sourceId(),next,Instant.now(),RuntimeEventKind.GAP,null,null,null,null,parent.eventId(),parent.eventId(),Map.of(),Map.of("diagnostic",diagnostic),new SourceWatermark(sourceId(),next),Completeness.PARTIAL,List.of());
            }
            callback.accept(event);
        }
    }
    @Override public synchronized SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence);}
    @Override public synchronized String topologyFingerprint(){return AdapterEvidence.digest(String.join("\n",latest.keySet()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Override public synchronized List<RuntimeFact> capture(){return List.copyOf(latest.values());}
    @Override public synchronized LocalCut captureCut(){var mark=watermark();return new LocalCut(mark,mark,capture());}
    @Override public Completeness completeness(){return Completeness.PARTIAL;}
    @Override public synchronized Map<String,Object> captureMetadata(){return Map.of("schemaVersion",VerificationObservation.VERSION,"populationProof","UNAVAILABLE_OBSERVED_SUBJECTS_ONLY","overflow",overflow,"capacity",capacity,"failure",failure);}
    @Override public synchronized void close(){if(closed)return;closed=true;ACTIVE.compareAndSet(this,null);observer=null;latest.clear();}
}
