package org.tzi.use.plugins.jacamo.codegrounded.use;

/** Selects the amount of native USE projection materialized for a build. */
public enum NativeProjectionMode {
    /** Verification-oriented projection used by the production native pipeline. */
    AUTO,
    /** Complete native projection retained for audit and debugging. */
    FULL
}
