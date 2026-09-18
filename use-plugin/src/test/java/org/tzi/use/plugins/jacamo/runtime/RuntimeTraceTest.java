package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
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
    private RuntimeEvent event(String id, long sequence) {
        return RuntimeEvent.create(id, Instant.EPOCH, sequence, Dimension.ENVIRONMENT,
            RuntimeEventKind.OP_EXIT, "runtime", null, Map.of(), "op");
    }
}
