package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.trace.*;

class RuntimeAliasTest {
    @Test void aliasesAreExactAndStaleRecordsCannotBeReused() {
        TraceIndex index = new TraceIndex(List.of(record("t", TraceRecord.Status.RESOLVED)));
        index.registerRuntimeKey("t", "jason:agent:a");
        index.registerRuntimeKey("t", "moise:agent:org/a");
        assertEquals(2, index.runtimeKeysFor("semantic:a").size());
        assertTrue(index.byRuntimeKey("jason:agent:A").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> index.registerRuntimeKey("t", "jason:agent:a"));
        TraceIndex stale = new TraceIndex(List.of(record("t", TraceRecord.Status.STALE)));
        stale.copyRuntimeKeysFrom(index);
        assertTrue(stale.runtimeKeysFor("semantic:a").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> stale.registerRuntimeKey("t", "a"));
        TraceIndex replacement = new TraceIndex(List.of(record("t", TraceRecord.Status.RESOLVED)));
        replacement.copyRuntimeKeysFrom(index);
        assertEquals(index.runtimeKeysFor("semantic:a"), replacement.runtimeKeysFor("semantic:a"));
    }
    private TraceRecord record(String id, TraceRecord.Status status) {
        return new TraceRecord(id, "semantic:a", "object:a", "Agent", "OBJECT", "rule", null, null, null, null, status);
    }
}
