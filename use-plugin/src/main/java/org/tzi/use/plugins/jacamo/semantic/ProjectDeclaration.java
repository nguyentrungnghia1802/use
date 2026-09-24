package org.tzi.use.plugins.jacamo.semantic;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;

/** Source project metadata, not an instance of an EClass absent from the active metamodel. */
public record ProjectDeclaration(String name, List<SourceProvenance> provenance,
                                 Map<String, AttributeValue> sourceFacts) {
    public ProjectDeclaration {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("project name required");
        provenance = List.copyOf(provenance);
        if (provenance.isEmpty()) throw new IllegalArgumentException("project provenance required");
        sourceFacts = Collections.unmodifiableMap(new TreeMap<>(sourceFacts));
    }
}
