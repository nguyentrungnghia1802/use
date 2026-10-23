package org.tzi.use.plugins.jacamo.semantics.profile;

import java.util.Locale;

/** Meaning of a projection, independent of AUTO/FULL materialization. Selected at import. */
public enum SemanticProjectionProfile {
    NATIVE_CURRENT, CSL_OBSERVER_V1, CSL_OBSERVER_V2, USE4GOAL_OBSERVED_V1, AUCTION_OBSERVED_V1, JACAMO_DECLARATIVE_XML;

    public static final String PROPERTY = "use.jacamo.semantics.profile";

    public static SemanticProjectionProfile configured(String text) {
        if (text == null || text.isBlank()) return NATIVE_CURRENT;
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("SEMANTIC_PROFILE_UNSUPPORTED:" + text, invalid);
        }
    }
}
