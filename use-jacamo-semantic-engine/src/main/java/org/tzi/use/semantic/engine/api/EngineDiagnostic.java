package org.tzi.use.semantic.engine.api;

import java.util.Objects;

/** A compiler/admission diagnostic. Unavailable semantics is never a violated contract. */
public record EngineDiagnostic(Stage stage, Kind kind, String code, String source,
                               int line, int column, String message) {
    public enum Stage { PARSE, MODEL_VALIDATION, CAPABILITY, BACKEND, OCL, RUNTIME }
    public enum Kind { ERROR, UNSUPPORTED }
    public EngineDiagnostic {
        Objects.requireNonNull(stage); Objects.requireNonNull(kind); Objects.requireNonNull(code);
        Objects.requireNonNull(source); Objects.requireNonNull(message);
        if (line < 0 || column < 0) throw new IllegalArgumentException("NEGATIVE_SOURCE_LOCATION");
    }
    /** Zero means no source location was supplied, not a fabricated first line. */
    public static EngineDiagnostic unsupported(Stage stage, String code, String source, String message) {
        return new EngineDiagnostic(stage, Kind.UNSUPPORTED, code, source, 0, 0, message);
    }
    public static EngineDiagnostic error(Stage stage, String code, String source, String message) {
        return new EngineDiagnostic(stage, Kind.ERROR, code, source, 0, 0, message);
    }
}
