package org.tzi.use.plugins.jacamo.project;

import java.nio.file.Path;
import java.util.Objects;
import org.tzi.use.plugins.jacamo.project.SourceSpan;

/** Directed edge from a declaring JCM file to the source it requires. */
public record ProjectEdge(Path from, Path to, ProjectEdgeKind kind, SourceSpan declaration) {
    public ProjectEdge {
        from = Objects.requireNonNull(from, "from").toAbsolutePath().normalize();
        to = Objects.requireNonNull(to, "to").toAbsolutePath().normalize();
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(declaration, "declaration");
    }
}
