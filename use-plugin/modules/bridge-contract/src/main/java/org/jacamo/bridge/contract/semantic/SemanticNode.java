package org.jacamo.bridge.contract.semantic;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Generic typed node payload used by Phase-1A DTOs not yet mapped to native USE. */
public record SemanticNode(
        SemanticMetadata metadata,
        Map<String, String> attributes,
        Map<String, List<String>> references) {
    public SemanticNode {
        metadata = SemanticSupport.required(metadata, "metadata");
        attributes = SemanticSupport.stringMap(attributes);
        var ordered = new TreeMap<String, List<String>>();
        if (references != null) references.forEach((key, value) ->
                ordered.put(SemanticSupport.required(key, "reference key"), List.copyOf(value)));
        references = java.util.Collections.unmodifiableMap(ordered);
    }
}
