package org.tzi.use.plugins.jacamo.runtime;

import org.tzi.use.plugins.jacamo.mapping.*;

/** Validates target anchors against the selected V2 descriptor, independently of connectors. */
public final class V2RuntimeBindingAdapter implements RuntimeBindingContract {
    private final MappingModel structural;
    public V2RuntimeBindingAdapter() { this(new ActiveBaseline().packaged().mapping()); }
    public V2RuntimeBindingAdapter(MappingModel structural) { this.structural = java.util.Objects.requireNonNull(structural); }
    @Override public void validate(RuntimeMapping.Rule rule) {
        var action = rule.action();
        boolean exists = switch (rule.anchor()) {
            case "classMappings" -> !structural.classes().isEmpty();
            case "referenceMappings" -> !structural.associations().isEmpty();
            case "orderProjection" -> !structural.orderProjections().isEmpty();
            default -> structural.projections().stream().anyMatch(p -> p.id().equals(rule.anchor()));
        };
        if (action.mutates() && !exists) fail(rule, "ANCHOR_MISSING");
        String expected = switch (action.target()) {
            case "ATTRIBUTE" -> "VP002";
            case "OPERATION" -> "VP003";
            case "OBJECT" -> "classMappings";
            case "ASSOCIATION" -> "referenceMappings";
            case "ORDER_NAVIGATION" -> "orderProjection";
            default -> "NONE";
        };
        if (!expected.equals(rule.anchor())) fail(rule, "ANCHOR_INCOMPATIBLE");
        if (action == RuntimeSemanticAction.RELATION_REORDER && (rule.eventKind() != RuntimeEventKind.REPLACE_ORDER
                || !rule.identity().equals("EXACT_TRACE"))) fail(rule, "ANCHOR_INCOMPATIBLE");
    }
    private void fail(RuntimeMapping.Rule rule, String code) {
        throw new RuntimeMappingException("RUNTIME_MAPPING_" + code, rule.id(), "anchor/targetKind");
    }
}
