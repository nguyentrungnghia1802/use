package org.tzi.use.plugins.jacamo.runtime;

import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.trace.TraceRecord;

/** Current structural/USE target adapter. Does not maintain a second identity index. */
public final class TraceRuntimeTargetAdapter implements RuntimeTargetResolver {
    private final TraceIndex trace;
    public TraceRuntimeTargetAdapter(TraceIndex trace) { this.trace = java.util.Objects.requireNonNull(trace); }
    @Override public Target resolve(Request request) {
        var record = trace.byRuntimeKey(request.runtimeId()).orElseThrow(() ->
            new IllegalArgumentException("RUNTIME_TRACE_UNRESOLVED:" + request.runtimeId()));
        if ((record.status() != TraceRecord.Status.RESOLVED && record.status() != TraceRecord.Status.PROJECTED)
                || !record.targetKind().equals(request.targetKind())
                || request.semanticId() != null && !request.semanticId().equals(record.sourceSemanticId()))
            throw new IllegalArgumentException("RUNTIME_TRACE_TARGET_MISMATCH:" + request.runtimeId());
        return new Target(record.sourceSemanticId(), record.targetUseId(), record);
    }
}
