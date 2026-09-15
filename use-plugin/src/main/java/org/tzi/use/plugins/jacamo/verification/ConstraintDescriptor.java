package org.tzi.use.plugins.jacamo.verification;

import java.nio.file.Path;
import java.util.List;
import org.tzi.use.plugins.jacamo.project.SourceSpan;

public record ConstraintDescriptor(String id, String name, String context, String operation,
                                   ConstraintKind kind, ConstraintOrigin origin, Path sourcePath,
                                   SourceSpan sourceSpan, List<String> dependencies, boolean enabled,
                                   String oclSource) {
    public ConstraintDescriptor {
        if (id == null || id.isBlank() || name == null || name.isBlank() || context == null || context.isBlank())
            throw new IllegalArgumentException("CONSTRAINT_DESCRIPTOR_INVALID");
        dependencies = List.copyOf(dependencies);
        oclSource = oclSource == null ? "" : oclSource;
    }
}
