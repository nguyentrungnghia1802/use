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
        this.buffered = new ArrayBlockingQueue<>(eventCapacity);
        this.liveObserver = java.util.Objects.requireNonNull(liveObserver);
        for (SnapshotSource source : this.sources) source.attach(this::observe);
    }

    private void observe(RuntimeEvent event) {
        java.util.Objects.requireNonNull(event, "event");
        synchronized (eventLock) {
            if (closed.get()) return;
            if (capturing) {
                if (!buffered.offer(event)) overflow.set(true);
                return;
            }
            liveObserver.accept(event);
        }
    }

    public Capture capture(String modelRevision, int maxAttempts) throws Exception {
        if (closed.get()) throw new IllegalStateException("SNAPSHOT_COORDINATOR_CLOSED");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            synchronized (eventLock) {
                overflow.set(false);
                capturing = true;
            }
            Instant startTime = Instant.now();
            var start = watermarks(); var topology = topology(); var facts = new ArrayList<RuntimeFact>();
            for (SnapshotSource source : sources) facts.addAll(source.capture());
            var end = watermarks(); var afterTopology = topology(); Instant endTime = Instant.now();
            boolean changedAuthoritativeSource = facts.stream()
                    .filter(fact -> fact.projectionStatus() == org.jacamo.bridge.contract.ProjectionStatus.MATERIALIZED_FAITHFULLY)
                    .map(fact -> fact.id().authority()).distinct()
                    .anyMatch(source -> !java.util.Objects.equals(start.get(source), end.get(source)));
            if (overflow.get() || !topology.equals(afterTopology) || regressed(start, end) || changedAuthoritativeSource) {
                // Do not discard events observed during a rejected cut.  A
                // later accepted cut either includes them in its watermark or
                // replays them after its watermark; dropping them would create
                // a false per-source sequence gap for a live mirror.
                synchronized (eventLock) { capturing = false; }
                continue;
            }
            String fingerprint = digest(CanonicalJson.encode(Map.of("modelRevision", modelRevision,
                    "facts", facts.stream().map(fact -> Map.of("id", fact.id().canonical(), "kind", fact.kind().name(), "values", fact.values(), "projection", fact.projectionStatus().name())).toList())));
            var completeness = new TreeMap<String, Completeness>(); sources.forEach(source -> completeness.put(source.sourceId(), source.completeness()));
            var snapshot = new RuntimeSnapshot("snapshot:" + fingerprint, modelRevision, startTime, endTime, start, end,
                    attempt, facts, completeness, fingerprint);
            List<RuntimeEvent> replay;
            synchronized (eventLock) {
                replay = buffered.stream().filter(event -> event.sourceSequence() >
                        end.getOrDefault(event.sourceId(), new SourceWatermark(event.sourceId(), 0)).sequence()).toList();
                for (RuntimeEvent event : replay) liveObserver.accept(event);
                buffered.clear();
                capturing = false;
            }
            return new Capture(snapshot, replay);
        }
        synchronized (eventLock) { capturing = false; buffered.clear(); }
        throw new ContractException("SNAPSHOT_CUT_NOT_STABLE");
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
