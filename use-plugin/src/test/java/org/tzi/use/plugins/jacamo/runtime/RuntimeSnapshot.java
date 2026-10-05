package org.tzi.use.plugins.jacamo.runtime;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record RuntimeSnapshot(String snapshotId, Instant capturedAt, long sequence,
                              List<RuntimeEvent> mutations, String fingerprint) {
    public RuntimeSnapshot {
        if (snapshotId == null || snapshotId.isBlank() || capturedAt == null || sequence < 0
                || fingerprint == null || fingerprint.isBlank())
            throw new IllegalArgumentException("RUNTIME_SNAPSHOT_INVALID");
        mutations = mutations.stream().sorted(Comparator.comparingLong(RuntimeEvent::sequence)).toList();
        if (mutations.stream().anyMatch(event -> event.sequence() > sequence))
            throw new IllegalArgumentException("RUNTIME_SNAPSHOT_SEQUENCE_INVALID");
    }
}
