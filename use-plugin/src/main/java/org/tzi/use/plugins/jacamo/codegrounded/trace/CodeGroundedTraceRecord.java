package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;

/** Trace record kept separate from the historical mapping-V2 trace schema. */
public record CodeGroundedTraceRecord(
        String ruleId,
        TracePhase phase,
        String sourceKind,
        String sourceJavaFqcn,
        String sourceIdentity,
        String targetKind,
        String targetIdentity,
        EvidenceAuthority evidenceAuthority,
        Fidelity fidelity,
        CapabilityStatus capabilityStatus,
        List<String> diagnostics) {
    public CodeGroundedTraceRecord {
        for (String value : List.of(ruleId, sourceKind, sourceJavaFqcn, sourceIdentity, targetKind, targetIdentity))
            if (value == null || value.isBlank()) throw new IllegalArgumentException("CODE_GROUNDED_TRACE_FIELD_REQUIRED");
        java.util.Objects.requireNonNull(phase, "phase");
        java.util.Objects.requireNonNull(evidenceAuthority, "evidenceAuthority");
        java.util.Objects.requireNonNull(fidelity, "fidelity");
        java.util.Objects.requireNonNull(capabilityStatus, "capabilityStatus");
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
