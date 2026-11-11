package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.Objects;

/**
 * Resolved target symbol indexed from compiled CSL or iStar models.
 */
public record TargetSymbol(
        TargetKind kind,
        String name,
        String owner,
        String declaredType,
        boolean optional,
        String modelSource) {

    public enum TargetKind {
        CSL_ENTITY,
        CSL_ROLE,
        CSL_GROUP,
        CSL_ATTRIBUTE,
        ISTAR_ACTOR,
        ISTAR_GOAL,
        ISTAR_TASK,
        ISTAR_RESOURCE,
        ISTAR_QUALITY
    }

    public TargetSymbol {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(modelSource, "modelSource");
    }

    public boolean isCsl() {
        return "CSL".equalsIgnoreCase(modelSource);
    }

    public boolean isIStar() {
        return "ISTAR".equalsIgnoreCase(modelSource);
    }

    public String qualifiedName() {
        return (owner != null && !owner.isBlank()) ? owner + "." + name : name;
    }
}

