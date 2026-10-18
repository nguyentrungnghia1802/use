package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.Objects;

/** Mapping admission diagnostic, never a runtime verification verdict. Line zero means unavailable. */
public record MappingDiagnostic(Kind kind,String code,String sourceId,String targetId,String ruleId,String origin,int line,String reason) {
    public enum Kind { ERROR, UNSUPPORTED, TRACE_ONLY }
    public MappingDiagnostic {Objects.requireNonNull(kind);Objects.requireNonNull(code);Objects.requireNonNull(sourceId);Objects.requireNonNull(targetId);Objects.requireNonNull(ruleId);Objects.requireNonNull(origin);Objects.requireNonNull(reason);if(line<0)throw new IllegalArgumentException("SOURCE_LINE_INVALID");}
}
