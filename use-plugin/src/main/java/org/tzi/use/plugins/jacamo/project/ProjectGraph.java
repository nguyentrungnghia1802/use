package org.tzi.use.plugins.jacamo.project;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable source graph; semantic parsing belongs to Phase 3. */
public record ProjectGraph(ProjectRoot root, Path entry, List<SourceFile> sources, List<ProjectEdge> edges) {
    public ProjectGraph {
        Objects.requireNonNull(root, "root");
        entry = Objects.requireNonNull(entry, "entry").toAbsolutePath().normalize();
        sources = sources.stream().sorted(Comparator.comparing(source -> source.path().toString())).toList();
        edges = edges.stream().sorted(Comparator.comparing((ProjectEdge edge) -> edge.from().toString())
                .thenComparing(edge -> edge.to().toString()).thenComparing(edge -> edge.kind().name())).toList();
    }
}
