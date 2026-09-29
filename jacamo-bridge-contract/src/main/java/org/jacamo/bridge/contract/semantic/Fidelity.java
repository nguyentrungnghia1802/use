package org.jacamo.bridge.contract.semantic;

/** Semantic fidelity of a code-grounded fact or transformation result. */
public enum Fidelity {
    EXACT,
    SUPPORTED_SUBSET,
    CONDITIONAL,
    RUNTIME_ONLY,
    PROVENANCE_ONLY,
    REPRESENTATION_LOSS,
    UNKNOWN,
    UNSUPPORTED
}
