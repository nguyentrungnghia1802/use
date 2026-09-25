package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

class RuntimeTraceTest {
    @Test void orderedHistoryRejectsDuplicatesAndRetiredGenerations() {
        RuntimeTrace trace = new RuntimeTrace();
        long first = trace.begin("CONNECT");
        trace.accept(first, event("a", 1)); trace.accept(first, event("b", 2));
        assertThrows(IllegalArgumentException.class, () -> trace.accept(first, event("a", 3)));
        assertThrows(IllegalArgumentException.class, () -> trace.accept(first, event("c", 2)));
        assertThrows(IllegalArgumentException.class, () -> trace.accept(first, event("d", 0)));
        trace.close(); long second = trace.begin("RECONNECT");
        assertThrows(IllegalArgumentException.class, () -> trace.accept(first, event("late", 4)));
        trace.accept(second, event("a", 1));
        assertEquals(2, trace.boundaries().size());
        assertEquals(3, trace.byEventId("a").size());
        assertEquals(7, trace.byCorrelation("op").size());
        assertEquals(1, trace.range(second, 1, 1).size());
        assertEquals(trace.entries(), trace.entries());
    }

    @Test void evidenceAndReplayWindowsRetireOldestEntriesDeterministically() {
        RuntimeTrace trace = new RuntimeTrace(3, 2, 2);
        long owner = trace.begin("FIRST");
        trace.accept(owner, event("a", 1));
        trace.result(owner, event("a", 1), MutationResult.applied());
        trace.accept(owner, event("b", 2));
        trace.result(owner, event("b", 2), MutationResult.applied());
        trace.accept(owner, event("c", 3));
        trace.begin("SECOND");
        trace.begin("THIRD");

        RuntimeTrace.RetentionMetrics metrics = trace.retentionMetrics();
        assertEquals(3, metrics.entries());
        assertEquals(5, metrics.totalEntries());
        assertEquals(2, metrics.retiredEntries());
        assertEquals(2, metrics.boundaries());
        assertEquals(3, metrics.totalBoundaries());
        assertEquals(1, metrics.retiredBoundaries());
        assertEquals(1, metrics.retiredEventIds());
        assertEquals(List.of("SECOND", "THIRD"), trace.boundaries().stream()
                .map(RuntimeTrace.Boundary::reason).toList());
    }
    private RuntimeEvent event(String id, long sequence) {
        return RuntimeEvent.create(id, Instant.EPOCH, sequence, Dimension.ENVIRONMENT,
            RuntimeEventKind.OP_EXIT, "runtime", null, Map.of(), "op");
    }
}
