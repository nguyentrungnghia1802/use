package org.tzi.use.plugins.jacamo.extraction;

import java.util.List;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;

public record ImportResult(JaCaMoSemanticModel model, List<Diagnostic> diagnostics) {
    public ImportResult { diagnostics = List.copyOf(diagnostics); }
    public boolean success() {
        return model != null && diagnostics.stream().noneMatch(d ->
                d.severity() == Severity.ERROR || d.severity() == Severity.FATAL);
    }
}
