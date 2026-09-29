package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Bidirectional exact-identity index for code-grounded traces. */
public final class CodeGroundedTraceIndex {
    public static final int DEFAULT_WARNING_THRESHOLD = 50_000;

    public record Metrics(int recordCount, Map<String, Integer> byPhase,
                          Map<String, Integer> byTargetKind, int warningThreshold,
                          boolean warningThresholdExceeded) {
        public Metrics {
            if (recordCount < 0 || warningThreshold < 1) throw new IllegalArgumentException("TRACE_METRICS_INVALID");
            byPhase = immutable(byPhase);
            byTargetKind = immutable(byTargetKind);
        }

        private static Map<String, Integer> immutable(Map<String, Integer> values) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private final List<CodeGroundedTraceRecord> records;
    private final Map<String,List<CodeGroundedTraceRecord>> bySource;
    private final Map<String,List<CodeGroundedTraceRecord>> byTarget;

    public CodeGroundedTraceIndex(List<CodeGroundedTraceRecord> records) {
        this.records = List.copyOf(records);
        this.bySource = index(this.records, CodeGroundedTraceRecord::sourceIdentity);
        this.byTarget = index(this.records, CodeGroundedTraceRecord::targetIdentity);
    }

    public List<CodeGroundedTraceRecord> records() { return records; }
    public List<CodeGroundedTraceRecord> targetsForSource(String sourceIdentity) {
        return bySource.getOrDefault(sourceIdentity, List.of());
    }
    public List<CodeGroundedTraceRecord> sourcesForTarget(String targetIdentity) {
        return byTarget.getOrDefault(targetIdentity, List.of());
    }

    public Metrics metrics() { return metrics(DEFAULT_WARNING_THRESHOLD); }

    public Metrics metrics(int warningThreshold) {
        if (warningThreshold < 1) throw new IllegalArgumentException("TRACE_WARNING_THRESHOLD_INVALID");
        Map<String, Integer> byPhase = new TreeMap<>();
        Map<String, Integer> byTargetKind = new TreeMap<>();
        records.forEach(record -> {
            byPhase.merge(record.phase().name(), 1, Integer::sum);
            byTargetKind.merge(record.targetKind(), 1, Integer::sum);
        });
        return new Metrics(records.size(), byPhase, byTargetKind, warningThreshold,
                records.size() > warningThreshold);
    }

    private static Map<String,List<CodeGroundedTraceRecord>> index(List<CodeGroundedTraceRecord> records,
            java.util.function.Function<CodeGroundedTraceRecord,String> key) {
        Map<String,List<CodeGroundedTraceRecord>> mutable = new LinkedHashMap<>();
        records.forEach(record -> mutable.computeIfAbsent(key.apply(record), ignored -> new ArrayList<>()).add(record));
        Map<String,List<CodeGroundedTraceRecord>> result = new LinkedHashMap<>();
        mutable.forEach((identity, values) -> result.put(identity, List.copyOf(values)));
        return Collections.unmodifiableMap(result);
    }
}
