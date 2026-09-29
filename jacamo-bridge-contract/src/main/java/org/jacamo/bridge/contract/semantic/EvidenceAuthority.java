package org.jacamo.bridge.contract.semantic;

/** Audited authority that established a semantic fact. */
public enum EvidenceAuthority {
    OFFICIAL_JACAMO_API,
    OFFICIAL_JASON_API,
    OFFICIAL_CARTAGO_API,
    OFFICIAL_MOISE_API,
    OFFICIAL_GENERATED_LEXER,
    RUNTIME_OBSERVATION,
    EXPLICIT_BINDING,
    UNAVAILABLE
}
