package org.tzi.use.plugins.jacamo.runtime;

import java.util.*;

/** Bounded in-memory evidence window. Exported evidence records total and retired-entry counts. */
public final class RuntimeTrace {
    public record Boundary(long generation, String reason) { }
    public record Entry(long generation, RuntimeEvent event, String disposition, String diagnostic) { }
    public record RetentionMetrics(int entries, long totalEntries, long retiredEntries,
                                   int boundaries, long totalBoundaries, long retiredBoundaries,
                                   int acceptedEventIds, long retiredEventIds) { }
    private final List<Entry> entries = new ArrayList<>();
    private final List<Boundary> boundaries = new ArrayList<>();
    private final LinkedHashSet<String> acceptedIds = new LinkedHashSet<>();
    private final int entryLimit;
    private final int boundaryLimit;
    private final int eventIdLimit;
    private long totalEntries;
    private long retiredEntries;
    private long totalBoundaries;
    private long retiredBoundaries;
    private long retiredEventIds;
    private long generation;
    private long lastSequence = -1;
    private boolean open;

    public RuntimeTrace() {
        this(RuntimeRetention.TRACE_ENTRIES, RuntimeRetention.TRACE_BOUNDARIES,
                RuntimeRetention.TRACE_EVENT_IDS);
    }

    RuntimeTrace(int entryLimit, int boundaryLimit, int eventIdLimit) {
        this.entryLimit = RuntimeRetention.requirePositive(entryLimit, "traceEntries");
        this.boundaryLimit = RuntimeRetention.requirePositive(boundaryLimit, "traceBoundaries");
        this.eventIdLimit = RuntimeRetention.requirePositive(eventIdLimit, "traceEventIds");
    }

    public synchronized long begin(String reason) {
        generation++; lastSequence = -1; acceptedIds.clear(); open = true;
        totalBoundaries++;
        if (RuntimeRetention.append(boundaries, new Boundary(generation, Objects.requireNonNull(reason)), boundaryLimit))
            retiredBoundaries++;
        return generation;
    }
    public synchronized void close() { open = false; }
    public synchronized long generation() { return generation; }
    public synchronized void accept(long owner, RuntimeEvent event) {
        String error = !open || owner != generation ? "RUNTIME_TRACE_RETIRED_GENERATION"
            : acceptedIds.contains(event.eventId()) ? "RUNTIME_TRACE_DUPLICATE_EVENT"
            : event.sequence() <= lastSequence ? "RUNTIME_TRACE_OUT_OF_ORDER" : null;
        if (error != null) {
            reject(owner, event, error);
            throw new IllegalArgumentException(error);
        }
        if (RuntimeRetention.remember(acceptedIds, event.eventId(), eventIdLimit)) retiredEventIds++;
        lastSequence = event.sequence();
        append(new Entry(owner, event, "ACCEPTED", null));
    }
    public synchronized void result(long owner, RuntimeEvent event, MutationResult result) {
        append(new Entry(owner, event, result.status().name(), result.diagnostic()));
    }
    public synchronized void reject(long owner, RuntimeEvent event, String diagnostic) {
        append(new Entry(owner, event, "REJECTED", diagnostic));
    }
    public synchronized List<Entry> entries() { return List.copyOf(entries); }
    public synchronized List<Boundary> boundaries() { return List.copyOf(boundaries); }
    public synchronized List<Entry> byEventId(String id) {
        return entries.stream().filter(entry -> entry.event().eventId().equals(id)).toList();
    }
    public synchronized List<Entry> byCorrelation(String id) {
        return entries.stream().filter(entry -> Objects.equals(id, entry.event().correlationId())).toList();
    }
    public synchronized List<Entry> range(long owner, long from, long through) {
        return entries.stream().filter(entry -> entry.generation() == owner
            && entry.event().sequence() >= from && entry.event().sequence() <= through).toList();
    }
    public synchronized RetentionMetrics retentionMetrics() {
        return new RetentionMetrics(entries.size(), totalEntries, retiredEntries,
                boundaries.size(), totalBoundaries, retiredBoundaries,
                acceptedIds.size(), retiredEventIds);
    }
    private void append(Entry entry) {
        totalEntries++;
        if (RuntimeRetention.append(entries, entry, entryLimit)) retiredEntries++;
    }
}
