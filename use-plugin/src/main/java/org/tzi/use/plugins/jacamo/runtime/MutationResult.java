package org.tzi.use.plugins.jacamo.runtime;

public record MutationResult(MutationStatus status, String diagnostic) {
    public static MutationResult applied() { return new MutationResult(MutationStatus.APPLIED, null); }
    public static MutationResult quarantined(String diagnostic) {
        return new MutationResult(MutationStatus.QUARANTINED, diagnostic);
    }
    public static MutationResult failed(String diagnostic) { return new MutationResult(MutationStatus.FAILED, diagnostic); }
}
