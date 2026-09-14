package org.tzi.use.plugins.jacamo.semantic;

/** Null targetId means unresolved; original spelling remains available for diagnostics. */
public record SemanticReference(String feature, String originalSpelling, SemanticId targetId) {
    public SemanticReference {
        if (feature == null || feature.isBlank()) throw new IllegalArgumentException("feature required");
        if (originalSpelling == null || originalSpelling.isBlank()) {
            throw new IllegalArgumentException("reference spelling required");
        }
    }
}
