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
    private final AtomicBoolean overflow = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SnapshotCoordinator(List<SnapshotSource> sources, int eventCapacity) throws Exception {
        this.sources = List.copyOf(sources); this.buffered = new ArrayBlockingQueue<>(eventCapacity);
        for (SnapshotSource source : this.sources) source.attach(event -> { if (!buffered.offer(event)) overflow.set(true); });
    }

    public Capture capture(String modelRevision, int maxAttempts) throws Exception {
        if (closed.get()) throw new IllegalStateException("SNAPSHOT_COORDINATOR_CLOSED");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            overflow.set(false); buffered.clear(); Instant startTime = Instant.now();
            var start = watermarks(); var topology = topology(); var facts = new ArrayList<RuntimeFact>();
            for (SnapshotSource source : sources) facts.addAll(source.capture());
            var end = watermarks(); var afterTopology = topology(); Instant endTime = Instant.now();
            if (overflow.get() || !topology.equals(afterTopology) || regressed(start, end)) continue;
            String fingerprint = digest(CanonicalJson.encode(Map.of("modelRevision", modelRevision,
                    "facts", facts.stream().map(fact -> Map.of("id", fact.id().canonical(), "kind", fact.kind().name(), "values", fact.values(), "projection", fact.projectionStatus().name())).toList())));
            var completeness = new TreeMap<String, Completeness>(); sources.forEach(source -> completeness.put(source.sourceId(), source.completeness()));
            var snapshot = new RuntimeSnapshot("snapshot:" + fingerprint, modelRevision, startTime, endTime, start, end,
                    attempt, facts, completeness, fingerprint);
            var replay = buffered.stream().filter(event -> event.sourceSequence() > end.get(event.sourceId()).sequence()).toList();
            return new Capture(snapshot, replay);
        }
        throw new ContractException("SNAPSHOT_CUT_NOT_STABLE");
    }

    private Map<String,SourceWatermark> watermarks() { var map=new TreeMap<String,SourceWatermark>(); for(var source:sources) map.put(source.sourceId(),source.watermark()); return map; }
    private Map<String,String> topology() throws Exception { var map=new TreeMap<String,String>(); for(var source:sources) map.put(source.sourceId(),source.topologyFingerprint()); return map; }
    private boolean regressed(Map<String,SourceWatermark> start,Map<String,SourceWatermark> end) { return start.entrySet().stream().anyMatch(entry -> end.get(entry.getKey()).sequence() < entry.getValue().sequence()); }
    private static String digest(byte[] bytes) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception error){throw new IllegalStateException(error);} }
    @Override public void close() throws Exception { if(!closed.compareAndSet(false,true)) return; Exception failure=null; for(var source:sources){try{source.close();}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}} buffered.clear(); if(failure!=null)throw failure; }
}
