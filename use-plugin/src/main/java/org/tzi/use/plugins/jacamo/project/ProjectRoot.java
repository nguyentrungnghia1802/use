package org.tzi.use.plugins.jacamo.project;

import java.nio.file.Path;
import java.util.Objects;

/** Canonical local root and identity of one JaCaMo project. */
public record ProjectRoot(Path path, String projectId) {
    public ProjectRoot {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
    }
}
