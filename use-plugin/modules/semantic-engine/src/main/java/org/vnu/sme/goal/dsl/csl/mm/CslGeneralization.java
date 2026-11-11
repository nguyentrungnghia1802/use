package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

public record CslGeneralization(String specific, String general) {
    public CslGeneralization {
        Objects.requireNonNull(specific, "specific"); Objects.requireNonNull(general, "general");
    }
}
