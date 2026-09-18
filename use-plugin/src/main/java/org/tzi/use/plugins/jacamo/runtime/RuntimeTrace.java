package org.tzi.use.plugins.jacamo.runtime;

import java.util.*;

/** In-memory evidence ledger. No automatic eviction: export/rotate at an explicit stream boundary. */
public final class RuntimeTrace {
    public record Boundary(long generation, String reason) { }
    public record Entry(long generation, RuntimeEvent event, String disposition, String diagnostic) { }
    private final List<Entry> entries = new ArrayList<>();
    private final List<Boundary> boundaries = new ArrayList<>();
    private final Set<String> acceptedIds = new HashSet<>();
    private long generation;
    private long lastSequence = -1;
    private boolean open;

    public synchronized long begin(String reason) {
        generation++; lastSequence = -1; acceptedIds.clear(); open = true;
        boundaries.add(new Boundary(generation, Objects.requireNonNull(reason)));
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
        acceptedIds.add(event.eventId()); lastSequence = event.sequence();
        entries.add(new Entry(owner, event, "ACCEPTED", null));
    }
    public synchronized void result(long owner, RuntimeEvent event, MutationResult result) {
        entries.add(new Entry(owner, event, result.status().name(), result.diagnostic()));
    }
    public synchronized void reject(long owner, RuntimeEvent event, String diagnostic) {
        entries.add(new Entry(owner, event, "REJECTED", diagnostic));
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
}
