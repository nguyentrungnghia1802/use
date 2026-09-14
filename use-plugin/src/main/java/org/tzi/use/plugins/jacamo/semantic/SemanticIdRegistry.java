package org.tzi.use.plugins.jacamo.semantic;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Rejects duplicate source identities before they enter a semantic model. */
public final class SemanticIdRegistry {
    private final Set<SemanticId> ids = new HashSet<>();

    public void register(SemanticId id) {
        if (!ids.add(Objects.requireNonNull(id, "id"))) {
            throw new IllegalArgumentException("duplicate semantic ID: " + id);
        }
    }
}
