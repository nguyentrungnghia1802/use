package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.Locale;

/** Selects the amount of native USE projection materialized for a build. */
public enum NativeProjectionMode {
    /** Verification-oriented projection used by the production native pipeline. */
    AUTO,
    /** Complete native projection retained for audit and debugging. */
    FULL;

    public static final String PROPERTY = "use.jacamo.projection.mode";

    /** Missing configuration preserves AUTO; unsupported values fail closed. */
    public static NativeProjectionMode configured(String value) {
        if (value == null || value.isBlank()) return AUTO;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("NATIVE_PROJECTION_MODE_UNSUPPORTED:" + value
                    + " (expected AUTO or FULL)", error);
        }
    }
}
