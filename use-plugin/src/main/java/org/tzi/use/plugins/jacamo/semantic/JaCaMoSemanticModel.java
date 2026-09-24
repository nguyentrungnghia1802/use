package org.tzi.use.plugins.jacamo.semantic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.project.ProjectRoot;
import org.tzi.use.plugins.jacamo.project.SourceFile;

/** Immutable, deterministic normalized semantic IR and its source/symbol indexes. */
public final class JaCaMoSemanticModel {
    private final ProjectRoot projectRoot;
    private final SemanticElement mas;
    private final ProjectDeclaration declaration;
    private final SemanticKindRegistry registry;
    private final List<SemanticElement> elements;
    private final List<Diagnostic> diagnostics;
    private final Map<Path, SourceFile> sourceIndex;
    private final Map<String, List<SemanticId>> symbolIndex;

    public JaCaMoSemanticModel(ProjectRoot projectRoot, SemanticElement mas,
                               List<SemanticElement> otherElements, List<SourceFile> sources,
                               List<Diagnostic> diagnostics) {
        this(projectRoot, mas, new ProjectDeclaration(mas.name(), mas.provenance(), mas.attributes()),
                null, otherElements, sources, diagnostics);
    }

    public JaCaMoSemanticModel(ProjectRoot projectRoot, ProjectDeclaration declaration,
                              SemanticKindRegistry registry, List<SemanticElement> elements,
                              List<SourceFile> sources, List<Diagnostic> diagnostics) {
        this(projectRoot, null, declaration, Objects.requireNonNull(registry), elements, sources, diagnostics);
    }

    private JaCaMoSemanticModel(ProjectRoot projectRoot, SemanticElement mas, ProjectDeclaration declaration,
                               SemanticKindRegistry registry, List<SemanticElement> otherElements,
                               List<SourceFile> sources, List<Diagnostic> diagnostics) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.mas = mas;
        this.declaration = Objects.requireNonNull(declaration);
        this.registry = registry;
        if (mas != null && mas.kind() != MetamodelKind.MAS) throw new IllegalArgumentException("MAS root required");
        TreeMap<Path, SourceFile> sourceMap = new TreeMap<>(Comparator.comparing(Path::toString));
        for (SourceFile source : sources) {
            if (sourceMap.putIfAbsent(source.path(), source) != null) {
                throw new IllegalArgumentException("duplicate source: " + source.path());
            }
        }
        this.sourceIndex = Collections.unmodifiableMap(sourceMap);
        for (SourceProvenance origin : declaration.provenance()) {
            SourceFile source = sourceIndex.get(origin.span().path());
            if (source == null || !source.sha256().equals(origin.sourceHash()))
                throw new IllegalArgumentException("project provenance missing or stale: " + origin.span().path());
        }
        ArrayList<SemanticElement> ordered = new ArrayList<>(otherElements);
        ordered.sort(Comparator.comparing(element -> element.id().value()));
        if (mas != null) ordered.addFirst(mas);
        SemanticIdRegistry ids = new SemanticIdRegistry();
        TreeMap<String, List<SemanticId>> symbols = new TreeMap<>();
        for (SemanticElement element : ordered) {
            ids.register(element.id());
            if (!projectRoot.projectId().equals(element.id().projectId())) {
                throw new IllegalArgumentException("element belongs to another project: " + element.id());
            }
            for (SourceProvenance origin : element.provenance()) {
                SourceFile source = sourceIndex.get(origin.span().path());
                if (source == null || !source.sha256().equals(origin.sourceHash())) {
                    throw new IllegalArgumentException("source provenance missing or stale: " + origin.span().path());
                }
            }
            symbols.computeIfAbsent(element.name(), ignored -> new ArrayList<>()).add(element.id());
        }
        symbols.replaceAll((name, values) -> values.stream().sorted(Comparator.comparing(SemanticId::value)).toList());
        this.symbolIndex = Collections.unmodifiableMap(symbols);
        this.elements = List.copyOf(ordered);
        if (registry != null) registry.validate(this.elements);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public String projectId() { return projectRoot.projectId(); }
    public ProjectRoot projectRoot() { return projectRoot; }
    public SemanticElement mas() { return mas; }
    public ProjectDeclaration declaration() { return declaration; }
    public SemanticKindRegistry registry() { return registry; }
    public List<SemanticElement> elements() { return elements; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public Map<Path, SourceFile> sourceIndex() { return sourceIndex; }
    public Map<String, List<SemanticId>> symbolIndex() { return symbolIndex; }
}
