package org.tzi.use.plugins.jacamo.semantic;

import java.util.Objects;
import org.tzi.use.plugins.jacamo.project.SourceSpan;

/** Exact source evidence used to create one semantic element. */
public record SourceProvenance(SourceSpan span, String parser, String sourceHash, String originalSpelling) {
    public SourceProvenance {
        Objects.requireNonNull(span, "span");
        if (parser == null || parser.isBlank()) throw new IllegalArgumentException("parser required");
        if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 source hash required");
        }
        Objects.requireNonNull(originalSpelling, "originalSpelling");
    }
}
