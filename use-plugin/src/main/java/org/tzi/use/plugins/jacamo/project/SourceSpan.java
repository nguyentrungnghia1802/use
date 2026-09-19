package org.tzi.use.plugins.jacamo.project;

import java.nio.file.Path;
import java.util.Objects;

/** Inclusive one-based source location. */
public record SourceSpan(Path path, int startLine, int startColumn, int endLine, int endColumn) {
    public SourceSpan {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (startLine < 1 || startColumn < 1 || endLine < startLine
                || (endLine == startLine && endColumn < startColumn) || endColumn < 1) {
            throw new IllegalArgumentException("invalid source span");
        }
    }
}
