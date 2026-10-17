package org.tzi.use.plugins.jacamo.semantics.evidence;

import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Boolean truth, undefined values, errors, unsupported constructs and missing evidence stay distinct. */
public enum SemanticVerdict {
    PASS, FAIL, UNDEFINED, ERROR, UNSUPPORTED, SKIPPED, INCONCLUSIVE;

    /** Conservative presentation for old consumers; the detailed result is retained separately. */
    public VerificationOutcome legacyView() {
        return switch (this) {
            case PASS -> VerificationOutcome.PASS;
            case FAIL -> VerificationOutcome.FAIL;
            case ERROR -> VerificationOutcome.ERROR;
            default -> VerificationOutcome.SKIPPED;
        };
    }
}
