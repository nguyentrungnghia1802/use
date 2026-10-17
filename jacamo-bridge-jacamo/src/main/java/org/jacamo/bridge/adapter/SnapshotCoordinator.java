package org.jacamo.bridge.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ContractException;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.jacamo.bridge.contract.SourceWatermark;

/** Buffer-first, per-source-watermarked validated cut. No wall-clock global ordering is invented. */
public final class SnapshotCoordinator implements AutoCloseable {
    public record Capture(RuntimeSnapshot snapshot, List<RuntimeEvent> replay) { }
    private final List<SnapshotSource> sources;
    private final ArrayBlockingQueue<RuntimeEvent> buffered;
    private final Consumer<RuntimeEvent> liveObserver;
    private final Object eventLock = new Object();
    private final AtomicBoolean overflow = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean capturing;

    public SnapshotCoordinator(List<SnapshotSource> sources, int eventCapacity) throws Exception {
        this(sources, eventCapacity, event -> { });
    }

    public SnapshotCoordinator(List<SnapshotSource> sources, int eventCapacity,
                               Consumer<RuntimeEvent> liveObserver) throws Exception {
        this.sources = List.copyOf(sources);
        if(this.sources.stream().map(SnapshotSource::sourceId).distinct().count()!=this.sources.size())throw new IllegalArgumentException("SNAPSHOT_DUPLICATE_SOURCE_SCOPE");
        this.buffered = new ArrayBlockingQueue<>(eventCapacity);
        this.liveObserver = java.util.Objects.requireNonNull(liveObserver);
        var attached=new ArrayList<SnapshotSource>();
        try {
            for (SnapshotSource source : this.sources) { attached.add(source);source.attach(this::observe); }
        } catch(Exception failure) {
            for(int i=attached.size()-1;i>=0;i--)try{attached.get(i).close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    private void observe(RuntimeEvent event) {
        java.util.Objects.requireNonNull(event, "event");
        synchronized (eventLock) {
            if (closed.get()) return;
            if (capturing) {
                if (!buffered.offer(event) && overflow.compareAndSet(false,true))
                    liveObserver.accept(new RuntimeEvent("snapshot-buffer-gap:"+event.eventId(),event.sessionId(),event.generation(),
                            event.modelRevision(),event.subsystem(),event.sourceId(),event.sourceSequence(),event.observedAt(),
                            org.jacamo.bridge.contract.RuntimeEventKind.GAP,null,null,null,null,event.correlationId(),event.eventId(),
                            Map.of(),Map.of("diagnostic","SNAPSHOT_CALLBACK_BUFFER_OVERFLOW"),event.watermark(),Completeness.PARTIAL,event.evidence()));
                return;
            }
            liveObserver.accept(event);
        }
    }

    public synchronized Capture capture(String modelRevision, int maxAttempts) throws Exception {
        if (closed.get()) throw new IllegalStateException("SNAPSHOT_COORDINATOR_CLOSED");
        if(maxAttempts<1)throw new IllegalArgumentException("SNAPSHOT_ATTEMPTS_MUST_BE_POSITIVE");
        var retryDiagnostics=new ArrayList<String>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            synchronized (eventLock) {
                overflow.set(false);
                capturing = true;
            }
            try {
            Instant startTime = Instant.now();
            var start=new TreeMap<String,SourceWatermark>();var end=new TreeMap<String,SourceWatermark>();var topology=topology();var facts=new ArrayList<RuntimeFact>();
            var authoritativeSources=new java.util.HashSet<String>();
            for (SnapshotSource source : sources) {var local=source.captureCut();start.put(source.sourceId(),local.start());var cut=local.facts();end.put(source.sourceId(),local.end());facts.addAll(cut);if(cut.stream().anyMatch(f->f.projectionStatus()==org.jacamo.bridge.contract.ProjectionStatus.MATERIALIZED_FAITHFULLY || f.kind()==org.jacamo.bridge.contract.RuntimeFactKind.NORM_INSTANCE && f.completeness()==Completeness.COMPLETE && "NPL_OBSERVATION/1.0".equals(f.values().get("observationVersion"))))authoritativeSources.add(source.sourceId());}
            var afterTopology = topology(); Instant endTime = Instant.now();
            // Fact authority (moise) is not the selected source scope (moise:org).
            var mutatingSources=authoritativeSources.stream().filter(source->!java.util.Objects.equals(start.get(source),end.get(source))).sorted().toList();
            boolean changedAuthoritativeSource = !mutatingSources.isEmpty();

            boolean duplicateIdentity=facts.stream().map(fact->fact.id().canonical()).distinct().count()!=facts.size();
            if (overflow.get() || duplicateIdentity || !topology.equals(afterTopology) || regressed(start, end) || changedAuthoritativeSource) {
                retryDiagnostics.add("attempt="+attempt+":overflow="+overflow.get()+":topologyChanged="+!topology.equals(afterTopology)+":watermarkRegressed="+regressed(start,end)+":authoritativeMutation="+mutatingSources);
                continue;
            }
            String fingerprint = digest(CanonicalJson.encode(Map.of("modelRevision", modelRevision,
                    "facts", facts.stream().map(fact -> Map.of("id", fact.id().canonical(), "kind", fact.kind().name(), "values", fact.values(), "projection", fact.projectionStatus().name())).toList())));
            var completeness = new TreeMap<String, Completeness>(); sources.forEach(source -> completeness.put(source.sourceId(), source.completeness()));
            var captureEvidence=new TreeMap<String,Object>();captureEvidence.putAll(Map.of("version","1.0","topologyStart",topology,"topologyEnd",afterTopology,"cutPolicy","PER_SOURCE_WATERMARK_AND_TOPOLOGY_VALIDATED","causality","NO_WALL_CLOCK_TOTAL_ORDER","rejectedAttempts",attempt-1,"retryDiagnostics",List.copyOf(retryDiagnostics),"sourceCutPolicy","LOCAL_CAPTURE_INTERVAL","globalAtomicity","UNSUPPORTED"));var sourceEvidence=new TreeMap<String,Object>();for(var selected:sources)if(!selected.captureMetadata().isEmpty())sourceEvidence.put(selected.sourceId(),selected.captureMetadata());if(!sourceEvidence.isEmpty())captureEvidence.put("sourceEvidence",sourceEvidence);
            var snapshot = new RuntimeSnapshot("snapshot:" + fingerprint, modelRevision, startTime, endTime, start, end,
                    attempt, facts, completeness, fingerprint,captureEvidence);
            List<RuntimeEvent> replay;
            synchronized (eventLock) {
                replay = buffered.stream().filter(event -> event.sourceSequence() >
                        end.getOrDefault(event.sourceId(), new SourceWatermark(event.sourceId(), 0)).sequence()).toList();
                publishBuffered();
            }
            return new Capture(snapshot, replay);
            } catch(Exception unavailable) {
                retryDiagnostics.add("attempt="+attempt+":"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());
                if(attempt==maxAttempts)throw new ContractException("SNAPSHOT_CUT_NOT_STABLE:"+retryDiagnostics,unavailable);
            } finally {
                // Existing subscribers have older cuts: even callbacks covered by
                // this snapshot must reach their stream. The capturing client can
                // discard them only using its own exact snapshot watermarks.
                synchronized(eventLock) {if(capturing)publishBuffered();}
            }
        }
        throw new ContractException("SNAPSHOT_CUT_NOT_STABLE:"+retryDiagnostics);
    }

    /** Called under eventLock; bounded non-blocking transport enqueue only. */
    private void publishBuffered() {
        try {for(RuntimeEvent event;(event=buffered.poll())!=null;)liveObserver.accept(event);}
        finally {buffered.clear();capturing=false;}
    }

    public void poll() throws Exception {
        if(!closed.get()) for(var source:sources) source.poll();
    }

    private Map<String,SourceWatermark> watermarks() { var map=new TreeMap<String,SourceWatermark>(); for(var source:sources) map.put(source.sourceId(),source.watermark()); return map; }
    private Map<String,String> topology() throws Exception { var map=new TreeMap<String,String>(); for(var source:sources) map.put(source.sourceId(),source.topologyFingerprint()); return map; }
    private boolean regressed(Map<String,SourceWatermark> start,Map<String,SourceWatermark> end) { return start.entrySet().stream().anyMatch(entry -> end.get(entry.getKey()).sequence() < entry.getValue().sequence()); }
    private static String digest(byte[] bytes) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception error){throw new IllegalStateException(error);} }
    @Override public void close() throws Exception {
        if(!closed.compareAndSet(false,true)) return;
        Exception failure=null;
        synchronized (eventLock) { capturing = false; buffered.clear(); }
        for (int index = sources.size() - 1; index >= 0; index--) {
            try { sources.get(index).close(); }
            catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        }
        if(failure!=null)throw failure;
    }
}
