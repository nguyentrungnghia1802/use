package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.List;

/** Bounded immutable journal view. Ordinal, not stateVersion, is record identity. */
public record RuntimeHistoryPage(List<Entry> entries, long nextOrdinal, long persistedEntries,
        long retainedFromOrdinal, boolean gap, String diagnostic, boolean persistedPage) {
    public RuntimeHistoryPage { entries = List.copyOf(entries); }
    public record Entry(long ordinal, String kind, String intervalId, RuntimeVerificationResult result, boolean persisted) {
        public boolean evidenceOnly() { return !result.outcomes().isEmpty() && result.outcomes().stream().allMatch(value -> value.constraintId().startsWith("OBSERVED:")); }
    }
    public static RuntimeHistoryPage empty() { return new RuntimeHistoryPage(List.of(), 0, 0, 0, false, "", false); }
}
