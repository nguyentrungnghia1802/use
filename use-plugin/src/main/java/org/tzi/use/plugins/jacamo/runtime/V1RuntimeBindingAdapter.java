package org.tzi.use.plugins.jacamo.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Read-only final V1 binding to the frozen V1 structural/projection vocabulary. */
public final class V1RuntimeBindingAdapter implements RuntimeBindingContract {
    private final JsonNode structural;
    public V1RuntimeBindingAdapter() {
        try (var input = getClass().getResourceAsStream("/org/tzi/use/plugins/jacamo/historical/version-1/jacamo-use-mapping-v1.json")) {
            this.structural = new ObjectMapper().readTree(input);
        } catch (Exception error) { throw new RuntimeMappingException("RUNTIME_MAPPING_ANCHOR_RESOURCE", "document", error.getMessage()); }
    }
    @Override public void validate(RuntimeMapping.Rule rule) {
        var action = rule.action();
        if (action.mutates() && !anchorExists(structural, rule.anchor())) fail(rule, "ANCHOR_MISSING", "anchor");
            String expectedAnchor = switch (action.target()) {
                case "ATTRIBUTE" -> "VP002";
                case "OPERATION" -> "VP003";
                case "OBJECT" -> "classMappings";
                case "ASSOCIATION" -> "referenceMappings";
                default -> "NONE";
            };
            if (!expectedAnchor.equals(rule.anchor())) fail(rule, "ANCHOR_INCOMPATIBLE", "anchor/targetKind");
    }
    private boolean anchorExists(JsonNode node, String anchor) {
        if ((anchor.equals("classMappings") || anchor.equals("referenceMappings")) && node.has(anchor)) return true;
        if (node.isObject() && anchor.equals(node.path("id").asText())) return true;
        for (JsonNode child : node) if (anchorExists(child, anchor)) return true;
        return false;
    }
    private void fail(RuntimeMapping.Rule rule, String code, String field) {
        throw new RuntimeMappingException("RUNTIME_MAPPING_" + code, rule.id(), field);
    }
}

