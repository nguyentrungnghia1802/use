package org.tzi.use.plugins.jacamo.verification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record VerificationReport(String schemaVersion, String runId, Instant timestamp, String mode,
                                 boolean structureValid, List<VerificationResult> results,
                                 Map<String, String> fingerprints) {
    public VerificationReport {
        results = List.copyOf(results);
        fingerprints = java.util.Collections.unmodifiableMap(new TreeMap<>(fingerprints));
    }
    public static VerificationReport offline(String runId, boolean structureValid, List<VerificationResult> results) {
        return offline(runId, structureValid, results, Map.of());
    }
    public static VerificationReport offline(String runId, boolean structureValid, List<VerificationResult> results,
                                             Map<String, String> fingerprints) {
        return new VerificationReport("1.0.0", runId, Instant.now(), "OFFLINE", structureValid, results, fingerprints);
    }
    public static VerificationReport operation(String runId, List<VerificationResult> results) {
        return operation(runId, results, Map.of());
    }
    public static VerificationReport operation(String runId, List<VerificationResult> results,
                                               Map<String, String> fingerprints) {
        return new VerificationReport("1.0.0", runId, Instant.now(), "OPERATION", true, results, fingerprints);
    }
}
