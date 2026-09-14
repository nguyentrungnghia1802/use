package org.tzi.use.plugins.jacamo.semantic;

import java.util.Locale;

/** Semantic dimension, independent of the USE target model. */
public enum Dimension {
    PROJECT, AGENT, ENVIRONMENT, ORGANISATION;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
