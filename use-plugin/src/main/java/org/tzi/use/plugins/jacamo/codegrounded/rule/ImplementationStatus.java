package org.tzi.use.plugins.jacamo.codegrounded.rule;

/** Closed implementation-state vocabulary required by the 105-rule contract. */
public enum ImplementationStatus {
    IMPLEMENTED,
    PLANNED_CAPABILITY_GATED,
    UNAVAILABLE_IN_AUDITED_API,
    EXPLICITLY_UNSUPPORTED
}
