package org.tzi.use.plugins.jacamo.runtime;

import java.time.Instant;
import java.util.List;

public record RuntimeDriftReport(String snapshotId, String fingerprint, Instant checkedAt,
                                 List<RuntimeDriftDifference> differences, DriftResyncPolicy policy,
                                 boolean resyncTriggered) {
    public RuntimeDriftReport {
        if (snapshotId == null || snapshotId.isBlank() || fingerprint == null || fingerprint.isBlank()
                || checkedAt == null || policy == null)
            throw new IllegalArgumentException("RUNTIME_DRIFT_REPORT_INVALID");
        differences = List.copyOf(differences);
    }
    public boolean drifted() { return !differences.isEmpty(); }
}
