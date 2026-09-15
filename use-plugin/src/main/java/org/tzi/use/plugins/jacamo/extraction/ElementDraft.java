package org.tzi.use.plugins.jacamo.extraction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;
import org.tzi.use.plugins.jacamo.semantic.SemanticId;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;
import org.tzi.use.plugins.jacamo.semantic.SourceProvenance;

final class ElementDraft {
    final SemanticId id;
    final MetamodelKind kind;
    final String name;
    final List<SourceProvenance> provenance;
    final Map<String, AttributeValue> attributes = new LinkedHashMap<>();
    final List<SemanticReference> references = new ArrayList<>();

    ElementDraft(SemanticId id, MetamodelKind kind, String name, SourceProvenance provenance) {
        this.id = id; this.kind = kind; this.name = name; this.provenance = new ArrayList<>(List.of(provenance));
    }

    SemanticElement freeze() {
        return new SemanticElement(id, kind, name, provenance, attributes, references);
    }
}
