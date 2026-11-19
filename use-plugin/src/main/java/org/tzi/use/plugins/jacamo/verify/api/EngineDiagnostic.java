package org.tzi.use.plugins.jacamo.verify.api;

import java.util.List;
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

    /** Structured import/admission rejection; no verification FAIL is implied. */
    public static final class AdmissionException extends IllegalArgumentException {
        private final List<EngineDiagnostic> diagnostics;
        public AdmissionException(List<EngineDiagnostic> diagnostics) { this(diagnostics, null); }
        public AdmissionException(List<EngineDiagnostic> diagnostics, Throwable cause) {
            super("ENGINE_ADMISSION_REJECTED:" + diagnostics, cause);
            this.diagnostics = List.copyOf(diagnostics);
            if (this.diagnostics.isEmpty()) throw new IllegalArgumentException("ADMISSION_DIAGNOSTIC_REQUIRED");
        }
        public List<EngineDiagnostic> diagnostics() { return diagnostics; }
    }
}
