package org.tzi.use.plugins.jacamo.binding;

public record BindingEntry(String source, String target, String kind, String reason, String sourceHash,
                           String provenance, Status status) {
    public BindingEntry {
        if (!canonical(source) || !canonical(target)) throw new IllegalArgumentException("BINDING_ID_INVALID");
        if (kind == null || kind.isBlank() || reason == null || reason.isBlank()
                || provenance == null || provenance.isBlank() || sourceHash == null || !sourceHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("BINDING_ENTRY_INVALID");
    }
    public static BindingEntry active(String source, String target, String kind, String reason,
                                      String sourceHash, String provenance) {
        return new BindingEntry(source, target, kind, reason, sourceHash, provenance, Status.ACTIVE);
    }
    public BindingEntry stale() { return new BindingEntry(source, target, kind, reason, sourceHash, provenance, Status.STALE); }
    private static boolean canonical(String value) { return value != null && value.matches("jacamo:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+"); }
    public enum Status { ACTIVE, STALE }
}
