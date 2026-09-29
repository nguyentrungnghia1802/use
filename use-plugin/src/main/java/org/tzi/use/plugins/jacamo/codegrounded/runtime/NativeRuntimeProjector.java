package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.uml.sys.MSystem;

/**
 * Exact-ID runtime adapter for CODE_GROUNDED_NATIVE.  The Bridge mirror remains the transport
 * authority; this class is the only native component that can mutate the active USE state.
 */
public final class NativeRuntimeProjector {
    public record ProjectionResult(int materialized, List<String> evidenceOnly,
                                   List<String> unavailable, List<String> rejected) {
        public ProjectionResult {
            evidenceOnly = List.copyOf(evidenceOnly);
            unavailable = List.copyOf(unavailable);
            rejected = List.copyOf(rejected);
        }
    }

    private final CodeGroundedNativePipeline.Result pipeline;
    private final String sessionId;
    private final long generation;
    private final String modelRevision;
    private final NativeRuntimeMutationEngine mutations;
    private final Map<String, Long> sourceWatermarks = new LinkedHashMap<>();
    private final Map<String, RuntimeFact> evidence = new LinkedHashMap<>();
    private final List<NativeRuntimeTraceRecord> trace = new ArrayList<>();
    private String snapshotId = "";
    private boolean resyncRequired;
    private NativeRuntimeMutationEngine.OclGate lastOclGate;

    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String sessionId,
                                  long generation, String modelRevision) {
        this(pipeline, sessionId, generation, modelRevision, new CodeGroundedRuntimeRuleRegistry());
    }

    public NativeRuntimeProjector(CodeGroundedNativePipeline.Result pipeline, String sessionId,
                                  long generation, String modelRevision,
                                  CodeGroundedRuntimeRuleRegistry registry) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.sessionId = required(sessionId, "sessionId");
        if (generation < 0) throw new IllegalArgumentException("generation");
        this.generation = generation;
        this.modelRevision = required(modelRevision, "modelRevision");
        this.mutations = new NativeRuntimeMutationEngine(pipeline.state().system(),
                pipeline.state().semanticObjectIndex(), registry);
        this.lastOclGate = mutations.validateOcl();
        if (!lastOclGate.passed()) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INITIAL_OCL_GATE_FAILED");
    }

    public MSystem system() { return pipeline.state().system(); }
    public NativeRuntimeMutationEngine mutations() { return mutations; }
    public String snapshotId() { synchronized (this) { return snapshotId; } }
    public boolean resyncRequired() { synchronized (this) { return resyncRequired; } }
    public NativeRuntimeMutationEngine.OclGate lastOclGate() { synchronized (this) { return lastOclGate; } }
    public Map<String, Long> sourceWatermarks() { synchronized (this) { return Map.copyOf(sourceWatermarks); } }
    public Map<String, RuntimeFact> evidence() { synchronized (this) { return Map.copyOf(evidence); } }
    public List<NativeRuntimeTraceRecord> trace() { synchronized (this) { return List.copyOf(trace); } }

    public synchronized ProjectionResult applySnapshot(RuntimeSnapshot snapshot) {
        requireModelRevision(snapshot.modelRevision());
        mutations.resetToBaseline();
        sourceWatermarks.clear();
        evidence.clear();
        trace.clear();
        snapshotId = snapshot.snapshotId();
        resyncRequired = false;
        List<String> materialized = new ArrayList<>();
        List<String> evidenceOnly = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        try {
            int ordinal = 0;
            for (RuntimeFact fact : snapshot.facts().stream()
                    .sorted(Comparator.comparing(value -> value.id().canonical())).toList()) {
                evidence.put(fact.id().canonical(), fact);
                if (fact.projectionStatus() == org.jacamo.bridge.contract.ProjectionStatus.UNAVAILABLE
                        || fact.completeness() == Completeness.UNAVAILABLE) {
                    unavailable.add(fact.id().canonical());
                    record(fact.id().canonical() + ":unavailable", fact.id().authority(), fact.id().canonical(),
                            NativeRuntimeMutationEngine.ApplyResult.evidence("R-NATIVE-UNAVAILABLE", fact.id().canonical(),
                                    "UNAVAILABLE"), ordinal++, 0);
                    continue;
                }
                var result = mutations.apply(fact.id(), snapshotBinding(fact),
                        fact.kind(), fact.projectionStatus(), fact.completeness(), fact.values());
                if (result.status() == NativeRuntimeMutationEngine.Status.REJECTED) {
                    rejected.add(fact.id().canonical());
                    record(fact.id().canonical() + ":rejected", fact.id().authority(), fact.id().canonical(),
                            result, ordinal++, 0);
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_MUTATION_REJECTED:" + result.diagnostic());
                }
                record(fact.id().canonical() + ":snapshot", fact.id().authority(), fact.id().canonical(),
                        result, ordinal++, 0);
                if (result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED)
                    materialized.add(fact.id().canonical());
                else evidenceOnly.add(fact.id().canonical());
            }
            snapshot.endWatermarks().forEach((source, watermark) -> sourceWatermarks.put(source, watermark.sequence()));
            lastOclGate = mutations.validateOcl();
            if (!lastOclGate.passed()) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SNAPSHOT_OCL_GATE_FAILED");
            return new ProjectionResult(materialized.size(), evidenceOnly, unavailable, rejected);
        } catch (RuntimeException error) {
            mutations.resetToBaseline();
            sourceWatermarks.clear();
            resyncRequired = true;
            throw error;
        }
    }

    public synchronized boolean apply(RuntimeEvent event) {
        requireIdentity(event);
        if (event.kind() == RuntimeEventKind.GAP || event.kind() == RuntimeEventKind.MODEL_REVISION_CHANGED) {
            resyncRequired = true;
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_RESYNC_REQUIRED:" + event.kind());
        }
        long previous = sourceWatermarks.getOrDefault(event.sourceId(), -1L);
        if (event.sourceSequence() <= previous) {
            resyncRequired = true;
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_EVENT_STALE:" + event.sourceId()
                    + ":" + event.sourceSequence() + " <= " + previous);
        }
        var result = mutations.apply(event.entityId(), event.relationId(), event.factKind(),
                event.projectionStatus(), event.completeness(), event.after());
        record(event.eventId(), event.sourceId(), event.entityId() == null ? "event" : event.entityId().canonical(),
                result, event.sourceSequence(), event.generation());
        if (result.status() == NativeRuntimeMutationEngine.Status.REJECTED) {
            resyncRequired = true;
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_MUTATION_REJECTED:" + result.diagnostic());
        }
        sourceWatermarks.put(event.sourceId(), event.sourceSequence());
        lastOclGate = mutations.validateOcl();
        return result.status() == NativeRuntimeMutationEngine.Status.MATERIALIZED;
    }

    private void record(String eventId, String sourceId, String runtimeIdentity,
                        NativeRuntimeMutationEngine.ApplyResult result, long sequence, long eventGeneration) {
        String targetSemantic = result.targetSemanticId().isBlank() ? "evidence:" + runtimeIdentity : result.targetSemanticId();
        String targetUse = result.targetUseId().isBlank() ? "evidence" : result.targetUseId();
        trace.add(new NativeRuntimeTraceRecord(result.ruleId().isBlank() ? "R-NATIVE-EVIDENCE" : result.ruleId(),
                TracePhase.RUNTIME_MUTATION, eventId, sourceId, runtimeIdentity, targetSemantic, targetUse,
                result.status().name(), sequence, sessionId, eventGeneration, modelRevision,
                result.diagnostic().isBlank() ? List.of() : List.of(result.diagnostic())));
    }

    private org.jacamo.bridge.contract.BridgeRelationId snapshotBinding(RuntimeFact fact) {
        List<org.jacamo.bridge.contract.BridgeRelationId> bindings = fact.relations().stream()
                .filter(value -> "runtime-model-binding".equals(value.relationKind())).toList();
        boolean faithfulMutation = fact.projectionStatus()
                == org.jacamo.bridge.contract.ProjectionStatus.MATERIALIZED_FAITHFULLY
                && fact.completeness() == Completeness.COMPLETE
                && fact.values().get("normalizedEventKind") instanceof String normalized
                && !normalized.isBlank();
        if (faithfulMutation && bindings.size() > 1)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_AMBIGUOUS:" + fact.id().canonical());
        return bindings.size() == 1 ? bindings.getFirst() : null;
    }

    private void requireIdentity(RuntimeEvent event) {
        if (!sessionId.equals(event.sessionId())) {
            resyncRequired = true;
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_SESSION_STALE");
        }
        if (generation != event.generation()) {
            resyncRequired = true;
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_GENERATION_STALE");
        }
        requireModelRevision(event.modelRevision());
    }

    private void requireModelRevision(String revision) {
        if (!modelRevision.equals(revision)) {
            resyncRequired = true;
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_MODEL_REVISION_STALE");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field);
        return value;
    }
}
