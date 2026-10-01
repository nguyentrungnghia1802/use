package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/** Exact typed identities only. All runtime state and verification pass through the single writer. */
public final class NativeRuntimeProjector implements AutoCloseable {
    public record RuntimeAlias(String runtimeIdentity, String targetSemanticId, String targetUseId,
            String sessionId, long generation, String modelRevision) { }
    public record ProjectionResult(int materialized, List<String> evidenceOnly, List<String> unavailable, List<String> rejected) {
        public ProjectionResult { evidenceOnly = List.copyOf(evidenceOnly); unavailable = List.copyOf(unavailable); rejected = List.copyOf(rejected); }
    }
    private final NativeRuntimeMutationEngine mutations;
    private final RuntimeVerificationCoordinator coordinator;
    private String sessionId, modelRevision, snapshotId = "";
    private long generation;
    private boolean resyncRequired;
    private EventLedger ledger = new EventLedger(8192);
    private final Map<String, Long> sourceWatermarks = new LinkedHashMap<>();
    private final Map<String, RuntimeFact> evidence = new LinkedHashMap<>();
    private final Map<String, RuntimeAlias> aliases = new LinkedHashMap<>();
    private final List<NativeRuntimeTraceRecord> trace = new ArrayList<>();
    private record ProtocolState(String session, long generation, String snapshotId, EventLedger ledger,
            Map<String, Long> watermarks, Map<String, RuntimeFact> evidence,
            Map<String, RuntimeAlias> aliases, List<NativeRuntimeTraceRecord> trace) { }

    private ProtocolState saveProtocol() {
        return new ProtocolState(sessionId, generation, snapshotId, ledger, Map.copyOf(sourceWatermarks),
                Map.copyOf(evidence), Map.copyOf(aliases), List.copyOf(trace));
    }
    private void restoreProtocol(ProtocolState state) {
        sessionId = state.session(); generation = state.generation(); snapshotId = state.snapshotId(); ledger = state.ledger();
        sourceWatermarks.clear(); sourceWatermarks.putAll(state.watermarks());
        evidence.clear(); evidence.putAll(state.evidence()); aliases.clear(); aliases.putAll(state.aliases());
        trace.clear(); trace.addAll(state.trace()); resyncRequired = true;
    }

    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation, String revision) {
        this(pipeline, session, generation, revision, new CodeGroundedRuntimeRuleRegistry());
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry) {
        this(pipeline, session, generation, revision, registry, null);
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation,
            String revision, Path runtimeDirectory) {
        this(pipeline, session, generation, revision, new CodeGroundedRuntimeRuleRegistry(), runtimeDirectory);
    }
    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry, Path runtimeDirectory) {
        this(pipeline.state().system(), pipeline.state().semanticObjectIndex(), session, generation, revision, registry,
                runtimeDirectory);
    }
    public NativeRuntimeProjector(MSystem system, Map<String, MObject> index, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry) {
        this(system, index, session, generation, revision, registry, null);
    }
    public NativeRuntimeProjector(MSystem system, Map<String, MObject> index, String session, long generation,
            String revision, CodeGroundedRuntimeRuleRegistry registry, Path runtimeDirectory) {
        if (session == null || session.isBlank() || revision == null || revision.isBlank() || generation < 0)
            throw new IllegalArgumentException("NATIVE_RUNTIME_IDENTITY_REQUIRED");
        this.sessionId = session; this.generation = generation; this.modelRevision = revision;
        mutations = new NativeRuntimeMutationEngine(system, index, registry);
        coordinator = runtimeDirectory == null
                ? new RuntimeVerificationCoordinator(mutations, session, generation, revision)
                : new RuntimeVerificationCoordinator(mutations, session, generation, revision, runtimeDirectory,
                        512, 64L * 1024 * 1024, true);
    }
    @Override public void close() { coordinator.close(); }
    public MSystem system() { return mutations.system(); }
    public NativeRuntimeMutationEngine mutations() { return mutations; }
    public RuntimeVerificationCoordinator coordinator() { return coordinator; }
    public String snapshotId() { return coordinator.read(() -> snapshotId); }
    public boolean resyncRequired() { return coordinator.read(() -> resyncRequired); }
    public Map<String, Long> sourceWatermarks() { return coordinator.read(() -> Map.copyOf(sourceWatermarks)); }
    public Map<String, RuntimeFact> evidence() { return coordinator.read(() -> Map.copyOf(evidence)); }
    public Map<String, RuntimeAlias> runtimeAliases() { return coordinator.read(() -> Map.copyOf(aliases)); }
    public List<NativeRuntimeTraceRecord> trace() { return coordinator.read(() -> List.copyOf(trace)); }
    public NativeRuntimeMutationEngine.OclGate lastOclGate() {
        var latest = coordinator.latest();
        boolean passed = latest != null && !latest.outcomes().isEmpty() && latest.outcomes().stream().allMatch(item ->
                item.outcome() == VerificationOutcome.PASS);
        return new NativeRuntimeMutationEngine.OclGate(true, passed, latest == null ? "" : latest.diagnostic());
    }
    public ProjectionResult applySnapshot(RuntimeSnapshot snapshot) { return applySnapshot(snapshot, sessionId, generation); }
    public ProjectionResult applySnapshot(RuntimeSnapshot snapshot, String session, long nextGeneration) {
        return coordinator.read(() -> {
        ProtocolState before = saveProtocol();
        try {
        return coordinator.transaction("SNAPSHOT", snapshot.snapshotId(), "snapshot", 0, snapshot.captureEndedAt(),
                ContractPayloads.runtime(snapshot), true, () -> {
            requireRevision(snapshot.modelRevision());
            mutations.resetToBaseline();
            List<String> materialized = new ArrayList<>(), evidenceOnly = new ArrayList<>(), unavailable = new ArrayList<>();
            List<Map.Entry<RuntimeFact, NativeRuntimeMutationEngine.ApplyResult>> applied = new ArrayList<>();
            for (RuntimeFact fact : snapshot.facts().stream().sorted(Comparator.comparingInt(NativeRuntimeProjector::rank)
                    .thenComparing(value -> value.id().canonical())).toList()) {
                if (fact.projectionStatus() == ProjectionStatus.UNAVAILABLE || fact.completeness() == Completeness.UNAVAILABLE) {
                    unavailable.add(fact.id().canonical()); continue;
                }
                var result = mutations.apply(fact.id(), binding(fact), fact.kind(), fact.projectionStatus(),
                        fact.completeness(), fact.values(), false);
                if (result.status() == NativeRuntimeMutationEngine.Status.REJECTED)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_MUTATION_REJECTED:" + result.diagnostic());
                applied.add(Map.entry(fact, result));
                (result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED ? materialized : evidenceOnly).add(fact.id().canonical());
            }
            if (!mutations.structureValid()) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_STRUCTURE_INVALID");
            coordinator.acceptedSnapshot(session, nextGeneration, snapshot.modelRevision(), snapshot.sourceCompleteness());
            sessionId = session; generation = nextGeneration;
            sourceWatermarks.clear(); aliases.clear(); evidence.clear(); trace.clear(); ledger = new EventLedger(8192);
            snapshot.facts().forEach(fact -> evidence.put(fact.id().canonical(), fact));
            snapshot.endWatermarks().forEach((source, watermark) -> sourceWatermarks.put(source, watermark.sequence()));
            int ordinal = 0;
            for (var entry : applied) record(entry.getKey().id().canonical() + ":snapshot", entry.getKey().id().authority(),
                    entry.getKey().id().canonical(), entry.getValue(), ordinal++);
            snapshotId = snapshot.snapshotId(); resyncRequired = false;
            return new RuntimeVerificationCoordinator.Mutation<>(new ProjectionResult(materialized.size(), evidenceOnly, unavailable, List.of()), true);
        });
        } catch (RuntimeException error) { restoreProtocol(before); throw error; }
        });
    }
    public boolean apply(RuntimeEvent event) {
        return coordinator.read(() -> {
            try {
                requireIdentity(event);
                if (event.projectionStatus() == ProjectionStatus.MATERIALIZED_FAITHFULLY && event.completeness() != Completeness.COMPLETE)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_AUTHORITATIVE_EVENT_INCOMPLETE");
                if (event.kind() == RuntimeEventKind.GAP || event.kind() == RuntimeEventKind.MODEL_REVISION_CHANGED)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_RESYNC_REQUIRED:" + event.kind());
                if (ledger.inspect(event, ContractPayloads.event(event)) == EventLedger.Result.DUPLICATE) return false;
                if (resyncRequired) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_RESYNC_REQUIRED");
                long previous = sourceWatermarks.getOrDefault(event.sourceId(), -1L);
                if (event.sourceSequence() <= previous)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_EVENT_STALE:" + event.sourceSequence());
                if (previous >= 0 && event.sourceSequence() != previous + 1)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SEQUENCE_GAP:" + previous + "->" + event.sourceSequence());
            } catch (RuntimeException error) {
                resyncRequired = true; coordinator.coverageGap(event, error.getMessage()); throw error;
            }
            ProtocolState before = saveProtocol();
            try {
            boolean materialized = coordinator.transaction("EVENT", event.eventId(), event.sourceId(), event.sourceSequence(),
                    event.observedAt(), ContractPayloads.event(event), false, () -> {
                var result = mutations.apply(event.entityId(), event.relationId(), event.factKind(), event.projectionStatus(),
                        event.completeness(), event.after(), false);
                if (result.status() == NativeRuntimeMutationEngine.Status.REJECTED) {
                    resyncRequired = true;
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_MUTATION_REJECTED:" + result.diagnostic());
                }
                sourceWatermarks.put(event.sourceId(), event.sourceSequence());
                record(event.eventId(), event.sourceId(), event.entityId() == null ? "event" : event.entityId().canonical(), result, event.sourceSequence());
                return new RuntimeVerificationCoordinator.Mutation<>(result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED,
                        result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED);
            });
            ledger.accept(event, ContractPayloads.event(event));
            return materialized;
            } catch (RuntimeException error) { restoreProtocol(before); throw error; }
        });
    }
    private void record(String eventId, String source, String identity, NativeRuntimeMutationEngine.ApplyResult result, long sequence) {
        if (result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED) {
            RuntimeAlias alias = new RuntimeAlias(identity, result.targetSemanticId(), result.targetUseId(), sessionId, generation, modelRevision);
            var previous = aliases.putIfAbsent(identity, alias);
            if (previous != null && !previous.equals(alias)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ALIAS_COLLISION:" + identity);
            if (system().state().objectByName(result.targetUseId()) == null) aliases.remove(identity);
        }
        if (trace.size() == 8192) trace.remove(0);
        trace.add(new NativeRuntimeTraceRecord(result.ruleId(), TracePhase.RUNTIME_MUTATION, eventId, source, identity,
                result.targetSemanticId().isBlank() ? "evidence:" + identity : result.targetSemanticId(),
                result.targetUseId().isBlank() ? "evidence" : result.targetUseId(), result.status().name(), sequence,
                sessionId, generation, modelRevision, List.of(result.diagnostic())));
    }
    private BridgeRelationId binding(RuntimeFact fact) {
        var bindings = fact.relations().stream().filter(value -> value.relationKind().equals("runtime-model-binding")).toList();
        if (bindings.size() > 1 && fact.projectionStatus() == ProjectionStatus.MATERIALIZED_FAITHFULLY)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_AMBIGUOUS");
        return bindings.size() == 1 ? bindings.getFirst() : null;
    }
    private void requireIdentity(RuntimeEvent event) {
        if (!sessionId.equals(event.sessionId())) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SESSION_STALE");
        if (generation != event.generation()) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_GENERATION_STALE");
        requireRevision(event.modelRevision());
    }
    private void requireRevision(String revision) {
        if (!modelRevision.equals(revision)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_MODEL_REVISION_STALE");
    }
    private static int rank(RuntimeFact fact) {
        Object value = fact.values().get("normalizedEventKind");
        if (!(value instanceof String kind)) return 100;
        return switch (kind) {
            case "UPSERT_CARTAGO_WORKSPACE" -> 10;
            case "UPSERT_CARTAGO_AGENT_IDENTITY" -> 20;
            case "UPSERT_CARTAGO_ARTIFACT" -> 30;
            case "UPSERT_CARTAGO_PROPERTY_SNAPSHOT" -> 40;
            default -> 50;
        };
    }
}
