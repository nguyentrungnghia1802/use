package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.util.ArrayList;
import java.util.List;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRule;

/** Mutable build-local collector; published traces and indexes are immutable. */
public final class CodeGroundedTraceCollector {
    private final List<CodeGroundedTraceRecord> records = new ArrayList<>();

    public void add(CodeGroundedRule rule, TracePhase phase, SemanticMetadata source,
                    String targetKind, String targetIdentity, List<String> diagnostics) {
        records.add(new CodeGroundedTraceRecord(rule.ruleId(), phase, source.sourceKind(),
                source.sourceJavaFqcn(), source.semanticId(), targetKind, targetIdentity,
                source.evidenceAuthority(), source.fidelity(), source.capabilityStatus(), diagnostics));
    }

    public void add(CodeGroundedTraceRecord record) { records.add(java.util.Objects.requireNonNull(record)); }
    public List<CodeGroundedTraceRecord> records() { return List.copyOf(records); }
    public CodeGroundedTraceIndex index() { return new CodeGroundedTraceIndex(records()); }
}
