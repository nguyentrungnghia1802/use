package org.tzi.use.plugins.jacamo.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable typed source node; parser-only facts remain separate from metamodel attributes. */
public record SemanticElement(SemanticId id, MetamodelKind kind, String name,
                              List<SourceProvenance> provenance, Map<String, AttributeValue> attributes,
                              List<SemanticReference> references, Map<String, AttributeValue> sourceFacts) {
    public SemanticElement(SemanticId id, MetamodelKind kind, String name,
            List<SourceProvenance> provenance, Map<String, AttributeValue> attributes, List<SemanticReference> references) {
        this(id, kind, name, provenance, attributes, references, Map.of());
    }
    public SemanticElement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        if (!id.metamodelKind().equals(kind.name()) || id.dimension() != kind.dimension()) {
            throw new IllegalArgumentException("semantic ID kind/dimension does not match Ecore kind");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        provenance = List.copyOf(provenance);
        if (provenance.isEmpty()) throw new IllegalArgumentException("source provenance required");
        attributes = java.util.Collections.unmodifiableMap(new TreeMap<>(attributes));
        references = List.copyOf(references);
        sourceFacts = java.util.Collections.unmodifiableMap(new TreeMap<>(sourceFacts));
    }
}
