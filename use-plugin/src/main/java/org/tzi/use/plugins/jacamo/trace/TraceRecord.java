package org.tzi.use.plugins.jacamo.trace;

import org.tzi.use.plugins.jacamo.project.SourceSpan;

public record TraceRecord(String traceId, String sourceSemanticId, String targetUseId, String sourceKind,
                          String targetKind, String mappingRuleId, String projectionRuleId,
                          SourceSpan sourceSpan, String sourceHash, String runtimeKey, Status status) {
    public TraceRecord withRuntimeKey(String key) { return new TraceRecord(traceId, sourceSemanticId, targetUseId,
            sourceKind, targetKind, mappingRuleId, projectionRuleId, sourceSpan, sourceHash, key, status); }
    public enum Status { RESOLVED, PROJECTED, AMBIGUOUS, UNRESOLVED, STALE }
}
