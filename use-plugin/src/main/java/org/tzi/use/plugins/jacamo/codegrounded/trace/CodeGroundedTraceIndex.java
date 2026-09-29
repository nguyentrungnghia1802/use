package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bidirectional exact-identity index for code-grounded traces. */
public final class CodeGroundedTraceIndex {
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

    private static Map<String,List<CodeGroundedTraceRecord>> index(List<CodeGroundedTraceRecord> records,
            java.util.function.Function<CodeGroundedTraceRecord,String> key) {
        Map<String,List<CodeGroundedTraceRecord>> mutable = new LinkedHashMap<>();
        records.forEach(record -> mutable.computeIfAbsent(key.apply(record), ignored -> new ArrayList<>()).add(record));
        Map<String,List<CodeGroundedTraceRecord>> result = new LinkedHashMap<>();
        mutable.forEach((identity, values) -> result.put(identity, List.copyOf(values)));
        return Collections.unmodifiableMap(result);
    }
}
