package org.tzi.use.plugins.jacamo.diagnostics;

import java.util.Objects;
import org.tzi.use.plugins.jacamo.project.SourceSpan;

/** Actionable finding. Nullable identity/location fields mean not applicable. */
public record Diagnostic(String code, Severity severity, Phase phase, SourceSpan sourceLocation,
                         String semanticId, String mappingRuleId, String message,
                         String evidence, String remediation) {
    public Diagnostic {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("diagnostic code required");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(phase, "phase");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("diagnostic message required");
        Objects.requireNonNull(evidence, "evidence");
        if (remediation == null || remediation.isBlank()) throw new IllegalArgumentException("remediation required");
    }
}
