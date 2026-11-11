package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.Objects;

/**
 * Resolved, typed CSL correspondence binding in the compiled observation plan.
 */
public record CslBinding(
        SourceReference sourceRef,
        TargetReference targetRef,
        SourceSymbol sourceSymbol,
        TargetSymbol targetSymbol,
        BindingKind kind,
        TypeConversion conversion) {

    public enum BindingKind {
        ENTITY_MAPPING,
        ROLE_MAPPING,
        GROUP_MAPPING,
        ATTRIBUTE_MAPPING
    }

    public enum TypeConversion {
        NONE,
        DIRECT,
        TO_STRING,
        TO_INTEGER,
        TO_REAL,
        TO_BOOLEAN
    }

    public CslBinding {
        Objects.requireNonNull(sourceRef, "sourceRef");
        Objects.requireNonNull(targetRef, "targetRef");
        Objects.requireNonNull(sourceSymbol, "sourceSymbol");
        Objects.requireNonNull(targetSymbol, "targetSymbol");
        Objects.requireNonNull(kind, "kind");
        conversion = Objects.requireNonNullElse(conversion, TypeConversion.DIRECT);
    }

    public boolean isAttribute() { return kind == BindingKind.ATTRIBUTE_MAPPING; }
    public SourceReference sourceReference() { return sourceRef; }
    public TargetReference targetReference() { return targetRef; }
}

