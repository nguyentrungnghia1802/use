package org.tzi.use.plugins.jacamo.runtime;

public record RuntimeDriftDifference(String diagnosticCode, String eventId, String runtimeSourceId,
                                     String target, String expected, String actual) {
    public RuntimeDriftDifference {
        if (diagnosticCode == null || diagnosticCode.isBlank() || eventId == null || eventId.isBlank()
                || runtimeSourceId == null || runtimeSourceId.isBlank() || target == null || target.isBlank())
            throw new IllegalArgumentException("RUNTIME_DRIFT_DIFFERENCE_INVALID");
        expected = expected == null ? "<null>" : expected;
        actual = actual == null ? "<null>" : actual;
    }
}
