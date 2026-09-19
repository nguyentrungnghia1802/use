package org.tzi.use.plugins.jacamo.verification;

import java.time.Instant;
import java.util.List;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEvent;

public record RuntimeVerificationReport(String schemaVersion, String reportId, Instant timestamp,
                                        MirrorState connectionState, long snapshotVersion,
                                        String snapshotFingerprint, RuntimeEvent event,
                                        VerificationReport verification, long latencyNanos,
                                        List<String> diagnostics, VerificationCheckpoint checkpoint,
                                        List<org.tzi.use.plugins.jacamo.trace.TraceRecord> provenance) {
    public RuntimeVerificationReport {
        if (!"1.0.0".equals(schemaVersion) || reportId == null || reportId.isBlank() || timestamp == null
                || connectionState == null || snapshotVersion < 0 || verification == null || latencyNanos < 0)
            throw new IllegalArgumentException("RUNTIME_VERIFICATION_REPORT_INVALID");
        snapshotFingerprint = snapshotFingerprint == null ? "" : snapshotFingerprint;
        diagnostics = List.copyOf(diagnostics);
        provenance = List.copyOf(provenance);
        java.util.Objects.requireNonNull(checkpoint);
    }

    public RuntimeVerificationReport(String schemaVersion, String reportId, Instant timestamp,
            MirrorState connectionState, long snapshotVersion, String snapshotFingerprint, RuntimeEvent event,
            VerificationReport verification, long latencyNanos, List<String> diagnostics) {
        this(schemaVersion, reportId, timestamp, connectionState, snapshotVersion, snapshotFingerprint,
            event, verification, latencyNanos, diagnostics, VerificationCheckpoint.DIAGNOSTIC, List.of());
    }

    public boolean hasViolation() {
        return verification.results().stream().anyMatch(result -> result.outcome() == VerificationOutcome.FAIL);
    }
}
