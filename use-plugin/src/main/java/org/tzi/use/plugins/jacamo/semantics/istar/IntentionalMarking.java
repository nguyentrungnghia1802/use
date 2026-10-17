package org.tzi.use.plugins.jacamo.semantics.istar;

import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;

/** Immutable episode evidence. Logical marking and quality of the current observation are separate. */
public record IntentionalMarking(String occurrenceId, Status raw, Status evaluated, Lifecycle lifecycle,
        SemanticVerdict quality, String episodeIdentity, String bindingToken, long firstSatisfied,
        long firstViolated, boolean historyGap, String diagnostic) {
    public enum Status { SATISFIED, VIOLATED, UNDETERMINED }
    public enum Lifecycle { ACTIVE, INACTIVE, UNKNOWN }
}
