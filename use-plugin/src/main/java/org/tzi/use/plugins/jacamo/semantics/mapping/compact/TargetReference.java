package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.Objects;

/**
 * Parsed target reference pointing into CSL or iStar model elements.
 */
public record TargetReference(String qualifier, String member, String raw) {

    public TargetReference {
        Objects.requireNonNull(raw, "raw");
    }

    public boolean isSimple() {
        return member == null || member.isBlank();
    }

    public String rawReference() { return raw; }
    public String classifier() { return qualifier; }
    public String actor() { return qualifier; }

    public static TargetReference parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException("TARGET_REFERENCE_EMPTY");

        int dot = trimmed.indexOf('.');
        if (dot < 0) {
            return new TargetReference(trimmed, null, trimmed);
        }
        String qualifier = trimmed.substring(0, dot);
        String member = trimmed.substring(dot + 1);
        if (qualifier.isBlank() || member.isBlank()) {
            throw new IllegalArgumentException("INVALID_TARGET_REFERENCE:" + trimmed);
        }
        return new TargetReference(qualifier, member, trimmed);
    }
}

