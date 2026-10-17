package org.jacamo.bridge.contract;

import java.util.Map;

/** Source cuts must not roll back any accepted source prefix within a runtime epoch. */
public final class SnapshotAdmission {
    private SnapshotAdmission() { }
    public static void require(RuntimeSnapshot snapshot, Map<String, Long> accepted) {
        if (!snapshot.startWatermarks().keySet().equals(snapshot.endWatermarks().keySet()))
            throw new ContractException("SNAPSHOT_WATERMARK_SCOPE_MISMATCH");
        new java.util.TreeMap<>(snapshot.endWatermarks()).forEach((source, end) -> {
            var start = snapshot.startWatermarks().get(source);
            if (!source.equals(start.sourceId()) || !source.equals(end.sourceId()) || end.sequence() < start.sequence())
                throw new ContractException("SNAPSHOT_WATERMARK_INVALID:" + source);
            if (end.sequence() < accepted.getOrDefault(source, -1L))
                throw new ContractException("SNAPSHOT_WATERMARK_STALE:" + source);
        });
        for (String source : new java.util.TreeSet<>(accepted.keySet()))
            if (!snapshot.endWatermarks().containsKey(source) && !source.equals(VerificationBoundary.SOURCE_ID))
                throw new ContractException("SNAPSHOT_ACCEPTED_SOURCE_MISSING:" + source);
    }
}
