package org.tzi.use.plugins.jacamo.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TraceIndex {
    private final boolean runtimeEligible;
    private final org.tzi.use.plugins.jacamo.mapping.MappingModel contract;
    private final Map<String, TraceRecord> records = new LinkedHashMap<>();
    private final Map<String, TraceRecord> runtimeAliases = new LinkedHashMap<>();
    public TraceIndex(List<TraceRecord> records) { this(records, null, true); }
    public TraceIndex(List<TraceRecord> records, org.tzi.use.plugins.jacamo.mapping.MappingModel contract) {
        this(records, contract, true);
    }
    private TraceIndex(List<TraceRecord> records, org.tzi.use.plugins.jacamo.mapping.MappingModel contract, boolean runtimeEligible) {
        this.contract = contract;
        this.runtimeEligible = runtimeEligible;
        records.forEach(this::put);
    }
    /** Persisted V1 wire records carry no complete active contract or live incarnation proof. */
    static TraceIndex archived(List<TraceRecord> records) { return new TraceIndex(records, null, false); }
    public boolean runtimeEligible() { return runtimeEligible; }
    private void put(TraceRecord record) {
        if (record.runtimeKey() != null && records.values().stream().anyMatch(r -> record.runtimeKey().equals(r.runtimeKey())))
            throw new IllegalArgumentException("runtimeKey already registered");
        if (records.putIfAbsent(record.traceId(), record) != null) throw new IllegalArgumentException("duplicate traceId");
    }
    public List<TraceRecord> records() { return records.values().stream().sorted(Comparator.comparing(TraceRecord::traceId)).toList(); }
    public List<TraceRecord> bySemanticId(String semanticId) { return select(record -> semanticId.equals(record.sourceSemanticId())); }
    public List<TraceRecord> byUseId(String useId) { return select(record -> useId.equals(record.targetUseId())); }
    public List<TraceRecord> byTargetKind(String kind) { return select(record -> kind.equals(record.targetKind())); }
    public Optional<TraceRecord> byRuntimeKey(String key) {
        if (!runtimeEligible) return Optional.empty();
        TraceRecord alias = runtimeAliases.get(key);
        if (alias != null) return Optional.of(alias);
        return records.values().stream().filter(record -> key.equals(record.runtimeKey())).findFirst();
    }
    public void registerRuntimeKey(String traceId, String runtimeKey) {
        if (!runtimeEligible) throw new IllegalArgumentException("RUNTIME_ALIAS_ARCHIVED_TRACE: rebuild from active source and bind current runtime");
        if (runtimeKey == null || runtimeKey.isBlank()) throw new IllegalArgumentException("runtimeKey required");
        if (byRuntimeKey(runtimeKey).isPresent()) throw new IllegalArgumentException("runtimeKey already registered");
        TraceRecord record = records.get(traceId); if (record == null) throw new IllegalArgumentException("traceId missing");
        if (record.status() != TraceRecord.Status.RESOLVED && record.status() != TraceRecord.Status.PROJECTED)
            throw new IllegalArgumentException("RUNTIME_ALIAS_TRACE_NOT_RESOLVED");
        if (record.runtimeKey() == null) records.put(traceId, record.withRuntimeKey(runtimeKey));
        else runtimeAliases.put(runtimeKey, record);
    }
    public List<String> runtimeKeysFor(String semanticId) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        records.values().stream().filter(record -> semanticId.equals(record.sourceSemanticId()))
            .filter(record -> record.runtimeKey() != null).forEach(record -> keys.add(record.runtimeKey()));
        runtimeAliases.forEach((key, record) -> { if (semanticId.equals(record.sourceSemanticId())) keys.add(key); });
        return List.copyOf(keys);
    }
    /** Carry exact runtime identities forward only when the same semantic object still exists. */
    public void copyRuntimeKeysFrom(TraceIndex previous) {
        if (!runtimeEligible || !previous.runtimeEligible || !java.util.Objects.equals(contract, previous.contract)) return;
        Map<String, TraceRecord> keys = new LinkedHashMap<>(previous.runtimeAliases);
        previous.records.values().stream().filter(record -> record.runtimeKey() != null)
                .forEach(record -> keys.put(record.runtimeKey(), record));
        keys.forEach((key, old) -> bySemanticId(old.sourceSemanticId()).stream()
                .filter(next -> next.status() == TraceRecord.Status.RESOLVED || next.status() == TraceRecord.Status.PROJECTED)
                .filter(next -> old.status() == TraceRecord.Status.RESOLVED || old.status() == TraceRecord.Status.PROJECTED)
                .filter(next -> next.targetKind().equals(old.targetKind()))
                .filter(next -> next.targetUseId().equals(old.targetUseId()))
                .filter(next -> next.sourceKind().equals(old.sourceKind()))
                .filter(next -> java.util.Objects.equals(next.sourceHash(), old.sourceHash()))
                .filter(next -> java.util.Objects.equals(next.mappingRuleId(), old.mappingRuleId()))
                .filter(next -> java.util.Objects.equals(next.projectionRuleId(), old.projectionRuleId()))
                .findFirst().ifPresent(next -> registerRuntimeKey(next.traceId(), key)));
    }

    private List<TraceRecord> select(java.util.function.Predicate<TraceRecord> predicate) {
        return records.values().stream().filter(predicate).sorted(Comparator.comparing(TraceRecord::traceId)).toList();
    }
}
