package org.tzi.use.plugins.jacamo.project;

import java.util.List;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;

/** Graph may be absent for an invalid entry; all failures carry diagnostics. */
public record ProjectDiscoveryResult(ProjectGraph graph, List<Diagnostic> diagnostics) {
    public ProjectDiscoveryResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean success() {
        return graph != null && diagnostics.stream().noneMatch(d ->
                d.severity() == Severity.ERROR || d.severity() == Severity.FATAL);
    }
}
