package org.tzi.use.plugins.jacamo.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TraceIndex {
    private final Map<String, TraceRecord> records = new LinkedHashMap<>();
    private final Map<String, TraceRecord> runtimeAliases = new LinkedHashMap<>();
    public TraceIndex(List<TraceRecord> records) { records.forEach(this::put); }
    private void put(TraceRecord record) {
        if (records.putIfAbsent(record.traceId(), record) != null) throw new IllegalArgumentException("duplicate traceId");
    }
    public List<TraceRecord> records() { return records.values().stream().sorted(Comparator.comparing(TraceRecord::traceId)).toList(); }
    public List<TraceRecord> bySemanticId(String semanticId) { return select(record -> semanticId.equals(record.sourceSemanticId())); }
    public List<TraceRecord> byUseId(String useId) { return select(record -> useId.equals(record.targetUseId())); }
    public List<TraceRecord> byTargetKind(String kind) { return select(record -> kind.equals(record.targetKind())); }
    public Optional<TraceRecord> byRuntimeKey(String key) {
        TraceRecord alias = runtimeAliases.get(key);
        if (alias != null) return Optional.of(alias);
        return records.values().stream().filter(record -> key.equals(record.runtimeKey())).findFirst();
    }
    public void registerRuntimeKey(String traceId, String runtimeKey) {
        if (runtimeKey == null || runtimeKey.isBlank()) throw new IllegalArgumentException("runtimeKey required");
        if (byRuntimeKey(runtimeKey).isPresent()) throw new IllegalArgumentException("runtimeKey already registered");
        TraceRecord record = records.get(traceId); if (record == null) throw new IllegalArgumentException("traceId missing");
        if (record.runtimeKey() == null) records.put(traceId, record.withRuntimeKey(runtimeKey));
        else runtimeAliases.put(runtimeKey, record);
    }
    /** Carry exact runtime identities forward only when the same semantic object still exists. */
    public void copyRuntimeKeysFrom(TraceIndex previous) {
        Map<String, TraceRecord> keys = new LinkedHashMap<>(previous.runtimeAliases);
        previous.records.values().stream().filter(record -> record.runtimeKey() != null)
                .forEach(record -> keys.put(record.runtimeKey(), record));
        keys.forEach((key, old) -> bySemanticId(old.sourceSemanticId()).stream()
                .filter(next -> next.targetKind().equals(old.targetKind()))
                .filter(next -> next.targetUseId().equals(old.targetUseId()))
                .findFirst().ifPresent(next -> registerRuntimeKey(next.traceId(), key)));
    }

    private List<TraceRecord> select(java.util.function.Predicate<TraceRecord> predicate) {
        return records.values().stream().filter(predicate).sorted(Comparator.comparing(TraceRecord::traceId)).toList();
    }
}
