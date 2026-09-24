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
    String projectName;
    final List<SourceProvenance> projectProvenance = new ArrayList<>();
    final java.util.Map<String, org.tzi.use.plugins.jacamo.semantic.AttributeValue> projectFacts = new java.util.LinkedHashMap<>();
    ElementDraft environment;
    private final java.util.Map<Path, String> sourceText = new java.util.HashMap<>();

    String read(Path path) throws java.io.IOException {
        Path key = path.toAbsolutePath().normalize();
        if (sourceText.containsKey(key)) return sourceText.get(key);
        byte[] bytes = java.nio.file.Files.readAllBytes(key);
        try {
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(source(key).sha256())) throw new java.io.IOException("SOURCE_CHANGED_DURING_IMPORT: " + key);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        sourceText.put(key, text); return text;
    }

    ExtractionContext(ProjectGraph graph, List<Diagnostic> diagnostics) {
        this.graph = graph;
        this.diagnostics = diagnostics;
    }

    ElementDraft element(MetamodelKind kind, String name, List<String> owner, Path path,
                         int line, int column, String parser, String spelling) {
        return elementWithLocalId(kind, name, name, owner, path, line, column, parser, spelling);
    }

    ElementDraft elementWithLocalId(MetamodelKind kind, String name, String localId, List<String> owner, Path path,
                         int line, int column, String parser, String spelling) {
        SourceProvenance provenance = provenance(path, line, column, parser, spelling);
        SemanticId id = SemanticId.of(graph.root().projectId(), kind.dimension(), kind.name(), owner, localId);
        ElementDraft draft = new ElementDraft(id, kind, name, provenance);
        elements.add(draft);
        return draft;
    }

    void addProvenance(ElementDraft draft, Path path, int line, int column, String parser, String spelling) {
        draft.provenance.add(provenance(path, line, column, parser, spelling));
    }

    SourceProvenance provenance(Path path, int line, int column, String parser, String spelling) {
        SourceFile source = source(path);
        if (parser.equals("jason-parser")) {
            try {
                String sourceLine = read(path).lines().skip(line - 1L).findFirst().orElse("");
                int exact = sourceLine.indexOf(spelling, Math.max(0, column - 1));
                if (exact >= 0) column = exact + 1;
            } catch (java.io.IOException error) { throw new IllegalArgumentException(error); }
        }
        String[] lines = spelling.split("\n", -1);
        int endLine = line + lines.length - 1;
        int endColumn = lines.length == 1 ? column + Math.max(0, spelling.length() - 1) : Math.max(1, lines[lines.length - 1].length());
        SourceSpan span = new SourceSpan(path, Math.max(1, line), Math.max(1, column), Math.max(1, endLine), Math.max(1, endColumn));
        return new SourceProvenance(span, parser, source.sha256(), spelling);
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
