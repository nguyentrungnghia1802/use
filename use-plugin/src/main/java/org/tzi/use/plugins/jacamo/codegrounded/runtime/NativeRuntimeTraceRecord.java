package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.List;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;

/** Runtime mutation/evidence trace kept separate from the historical V2 trace schema. */
public record NativeRuntimeTraceRecord(String ruleId, TracePhase phase, String eventId,
                                       String sourceId, String runtimeIdentity,
                                       String targetSemanticId, String targetUseId,
                                       String outcome, long sourceSequence,
                                       String sessionId, long generation, String modelRevision,
                                       List<String> diagnostics) {
    public NativeRuntimeTraceRecord {
        if (ruleId == null || ruleId.isBlank() || phase != TracePhase.RUNTIME_MUTATION
                || eventId == null || eventId.isBlank() || sourceId == null || sourceId.isBlank()
                || runtimeIdentity == null || runtimeIdentity.isBlank() || targetSemanticId == null
                || targetSemanticId.isBlank() || targetUseId == null || targetUseId.isBlank()
                || outcome == null || outcome.isBlank() || sourceSequence < 0
                || sessionId == null || sessionId.isBlank() || generation < 0
                || modelRevision == null || modelRevision.isBlank())
            throw new IllegalArgumentException("NATIVE_RUNTIME_TRACE_INVALID");
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
