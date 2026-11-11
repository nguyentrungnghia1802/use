package org.tzi.use.semantic.engine.use75;

import java.util.*;
import org.tzi.use.semantic.engine.api.EngineDiagnostic;
import org.tzi.use.uml.mm.*;
import org.vnu.sme.goal.dsl.csl.mm.*;
import static org.tzi.use.semantic.engine.api.EngineDiagnostic.*;

/** Frozen schema/binding metadata; it neither creates nor owns a runtime MSystem. */
public final class Use75ModelBinding {
    public enum Kind { ENTITY, ROLE, ORG_CONTEXT }
    public record Classifier(String declaration, String symbol, Kind kind) { }
    public record Attribute(String declaration, CslAttribute specification) { }
    private final CslModel csl;
    private final MModel model;
    private final M0ExtensionSpec extension;
    private final Map<String, Classifier> classifiers;
    private final Map<String, String> owners;
    private final Map<String, Attribute> attributes;
    private final Map<String, List<String>> endRoles;

    Use75ModelBinding(CslModel csl, MModel model, M0ExtensionSpec extension,
            Map<String, Classifier> classifiers, Map<String, String> owners,
            Map<String, Attribute> attributes, Map<String, List<String>> endRoles) {
        this.csl = csl; this.model = model; this.extension = extension;
        this.classifiers = Collections.unmodifiableMap(new LinkedHashMap<>(classifiers));
        this.owners = Collections.unmodifiableMap(new LinkedHashMap<>(owners));
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        var ends = new LinkedHashMap<String, List<String>>(); endRoles.forEach((k,v) -> ends.put(k, List.copyOf(v)));
        this.endRoles = Collections.unmodifiableMap(ends);
    }
    public CslModel cslModel() { return csl; }
    public MModel model() { return model; }
    public M0ExtensionSpec extension() { return extension; }
    public Map<String, Classifier> classifiers() { return classifiers; }
    public Map<String, String> declarationOwners() { return owners; }
    public Map<String, Attribute> attributes() { return attributes; }
    public Map<String, List<String>> associationEndRoles() { return endRoles; }
    public MClass classifier(String declaration) {
        var binding = classifiers.get(declaration);
        if (binding == null) throw new IllegalArgumentException("CSL_DECLARATION_UNRESOLVED:" + declaration);
        return model.getClass(binding.symbol());
    }
    /** Schema support and live writer/type-codec support are separate admission gates. */
    public List<EngineDiagnostic> liveDiagnostics(Set<String> exactCodecNames, boolean naryWriterAvailable) {
        var diagnostics = new ArrayList<EngineDiagnostic>();
        for (var attribute : attributes.values()) {
            String type = attribute.specification().type().sourceName();
            if (!exactCodecNames.contains(type)) diagnostics.add(unsupported(Stage.CAPABILITY, "EXACT_RUNTIME_CODEC_REQUIRED",
                    attribute.declaration() + "::" + attribute.specification().name(), type));
        }
        if (!naryWriterAvailable) for (var relation : csl.relations()) if (relation.endpoints().size() > 2)
            diagnostics.add(unsupported(Stage.CAPABILITY, "NARY_RUNTIME_WRITER_REQUIRED", relation.name(), "Schema is supported; live link writer must be verified separately"));
        return List.copyOf(diagnostics);
    }
    public void requireLiveCompatible(Set<String> exactCodecNames, boolean naryWriterAvailable) {
        var diagnostics = liveDiagnostics(exactCodecNames, naryWriterAvailable);
        if (!diagnostics.isEmpty()) throw new org.tzi.use.semantic.engine.api.EngineAdmissionException(diagnostics);
    }
}
