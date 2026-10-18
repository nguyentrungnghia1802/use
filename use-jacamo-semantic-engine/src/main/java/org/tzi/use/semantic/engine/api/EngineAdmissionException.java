package org.tzi.use.semantic.engine.api;

import java.util.List;

/** Structured import/admission rejection; no verification FAIL is implied. */
public final class EngineAdmissionException extends IllegalArgumentException {
    private final List<EngineDiagnostic> diagnostics;
    public EngineAdmissionException(List<EngineDiagnostic> diagnostics) { this(diagnostics, null); }
    public EngineAdmissionException(List<EngineDiagnostic> diagnostics, Throwable cause) {
        super("ENGINE_ADMISSION_REJECTED:" + diagnostics, cause);
        this.diagnostics = List.copyOf(diagnostics);
        if (this.diagnostics.isEmpty()) throw new IllegalArgumentException("ADMISSION_DIAGNOSTIC_REQUIRED");
    }
    public List<EngineDiagnostic> diagnostics() { return diagnostics; }
}
