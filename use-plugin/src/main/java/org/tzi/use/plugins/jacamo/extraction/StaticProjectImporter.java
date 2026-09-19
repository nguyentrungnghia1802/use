package org.tzi.use.plugins.jacamo.extraction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.JcmProjectLoader;
import org.tzi.use.plugins.jacamo.project.ProjectDiscoveryResult;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

/** Phase 3 static import pipeline, independent of USE and runtime APIs. */
public final class StaticProjectImporter {
    public ImportResult importProject(Path jcmPath) {
        ProjectDiscoveryResult discovery = new JcmProjectLoader().discover(jcmPath);
        List<Diagnostic> diagnostics = new ArrayList<>(discovery.diagnostics());
        if (discovery.graph() == null) return new ImportResult(null, sorted(diagnostics));
        ExtractionContext context = new ExtractionContext(discovery.graph(), diagnostics);
        new JcmSemanticParser().parse(context);
        new JasonSourceParser().parse(context);
        new CartagoSourceExtractor().parse(context);
        new MoiseXmlParser().parse(context);
        org.tzi.use.plugins.jacamo.binding.BindingFile bindings = null;
        Path bindingPath = discovery.graph().root().path().resolve("binding.json");
        if (java.nio.file.Files.exists(bindingPath)) {
            try {
                var hashes = context.elements.stream().collect(java.util.stream.Collectors.toMap(
                        element -> element.id.value(), element -> element.provenance.getFirst().sourceHash()));
                bindings = new org.tzi.use.plugins.jacamo.binding.BindingStore().read(bindingPath, hashes);
            } catch (IllegalArgumentException exception) {
                context.diagnostic("BINDING_INVALID", Severity.ERROR, Phase.RESOLUTION, null, null,
                        "Cannot load project binding.json", exception.getMessage(),
                        "Correct binding.json schema and source identities");
            }
        }
        new SemanticResolver().resolve(context, bindings);
        List<SemanticElement> frozen = context.elements.stream().map(ElementDraft::freeze).toList();
        SemanticElement mas = frozen.stream().filter(element -> element.kind() == MetamodelKind.MAS).findFirst().orElse(null);
        if (mas == null) {
            diagnostics.add(new Diagnostic("SEMANTIC_MAS_MISSING", Severity.FATAL, Phase.SEMANTIC_MODEL,
                    null, null, null, "Semantic MAS root was not produced", discovery.graph().entry().toString(),
                    "Correct the entry JCM mas declaration"));
            return new ImportResult(null, sorted(diagnostics));
        }
        try {
            JaCaMoSemanticModel model = new JaCaMoSemanticModel(discovery.graph().root(), mas,
                    frozen.stream().filter(element -> element != mas).toList(), discovery.graph().sources(), sorted(diagnostics));
            return new ImportResult(model, model.diagnostics());
        } catch (IllegalArgumentException exception) {
            diagnostics.add(new Diagnostic("SEMANTIC_MODEL_INVALID", Severity.FATAL, Phase.SEMANTIC_MODEL,
                    null, null, null, "Cannot construct semantic model", exception.getMessage(),
                    "Resolve duplicate identities or stale source provenance"));
            return new ImportResult(null, sorted(diagnostics));
        }
    }

    private List<Diagnostic> sorted(List<Diagnostic> diagnostics) {
        return diagnostics.stream().sorted(Comparator.comparing(Diagnostic::code)
                .thenComparing(diagnostic -> diagnostic.sourceLocation() == null ? "" : diagnostic.sourceLocation().path().toString())
                .thenComparingInt(diagnostic -> diagnostic.sourceLocation() == null ? 0 : diagnostic.sourceLocation().startLine())
                .thenComparing(Diagnostic::message)).toList();
    }
}
