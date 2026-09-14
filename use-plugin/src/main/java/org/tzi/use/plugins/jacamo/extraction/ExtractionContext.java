package org.tzi.use.plugins.jacamo.extraction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.ProjectGraph;
import org.tzi.use.plugins.jacamo.project.SourceFile;
import org.tzi.use.plugins.jacamo.project.SourceSpan;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticId;
import org.tzi.use.plugins.jacamo.semantic.SourceProvenance;

final class ExtractionContext {
    final ProjectGraph graph;
    final List<ElementDraft> elements = new ArrayList<>();
    final List<Diagnostic> diagnostics;

    ExtractionContext(ProjectGraph graph, List<Diagnostic> diagnostics) {
        this.graph = graph;
        this.diagnostics = diagnostics;
    }

    ElementDraft element(MetamodelKind kind, String name, List<String> owner, Path path,
                         int line, int column, String parser, String spelling) {
        SourceFile source = source(path);
        int end = Math.max(column, column + Math.max(0, spelling.length() - 1));
        SourceSpan span = new SourceSpan(path, Math.max(1, line), Math.max(1, column),
                Math.max(1, line), Math.max(1, end));
        SourceProvenance provenance = new SourceProvenance(span, parser, source.sha256(), spelling);
        SemanticId id = SemanticId.of(graph.root().projectId(), kind.dimension(), kind.name(), owner, name);
        ElementDraft draft = new ElementDraft(id, kind, name, provenance);
        elements.add(draft);
        return draft;
    }

    SourceFile source(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return graph.sources().stream().filter(source -> source.path().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("source is outside project graph: " + path));
    }

    void diagnostic(String code, Severity severity, Phase phase, SourceSpan span,
                    String semanticId, String message, String evidence, String remediation) {
        diagnostics.add(new Diagnostic(code, severity, phase, span, semanticId, null,
                message, evidence, remediation));
    }
}
