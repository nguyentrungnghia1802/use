package org.tzi.use.plugins.jacamo;

import java.util.Locale;

/** Selects the semantic authority. Bridge is the only production default. */
public enum SemanticAuthority {
    BRIDGE;

    public static SemanticAuthority configured(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("bridge")) return BRIDGE;
        if (value.equalsIgnoreCase("legacy-compatibility"))
            throw new IllegalArgumentException("SEMANTIC_AUTHORITY_REMOVED:legacy-compatibility");
        throw new IllegalArgumentException("SEMANTIC_AUTHORITY_UNSUPPORTED:" + value.toLowerCase(Locale.ROOT));
    }
}
