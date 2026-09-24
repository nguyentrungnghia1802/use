package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.trace.*;

class RuntimeAliasTest {
    @Test void rebuildDoesNotTransferAliasesAcrossChangedSourceOrRule() {
        TraceRecord original = record("t", TraceRecord.Status.RESOLVED);
        TraceIndex old = new TraceIndex(List.of(original));
        old.registerRuntimeKey("t", "jason:agent:a");
        for (TraceRecord changed : List.of(
                new TraceRecord("t", "semantic:a", "object:a", "Agent", "OBJECT", "different-rule", null, null, null, null, TraceRecord.Status.RESOLVED),
                new TraceRecord("t", "semantic:a", "object:a", "Agent", "OBJECT", "rule", null, null, "changed-hash", null, TraceRecord.Status.RESOLVED),
                new TraceRecord("t", "semantic:a", "object:a", "HistoricalKind", "OBJECT", "rule", null, null, null, null, TraceRecord.Status.RESOLVED))) {
            TraceIndex next = new TraceIndex(List.of(changed));
            next.copyRuntimeKeysFrom(old);
            assertTrue(next.runtimeKeysFor("semantic:a").isEmpty(), changed.toString());
        }
    }
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
