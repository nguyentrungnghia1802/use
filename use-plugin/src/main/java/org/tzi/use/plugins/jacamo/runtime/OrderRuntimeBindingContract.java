package org.tzi.use.plugins.jacamo.runtime;

import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;

/** Explicit opt-in working order contract; historical V1 runtime mappings do not acquire this action. */
public final class OrderRuntimeBindingContract implements RuntimeBindingContract {
    private final TransformationPlan structure;
    public OrderRuntimeBindingContract(TransformationPlan structure) { this.structure = java.util.Objects.requireNonNull(structure); }
    @Override public void validate(RuntimeMapping.Rule rule) {
        if (rule.action() != RuntimeSemanticAction.RELATION_REORDER || rule.eventKind() != RuntimeEventKind.REPLACE_ORDER ||
                !rule.anchor().equals("orderProjection") || structure.orderProjections().isEmpty() || !rule.identity().equals("EXACT_TRACE"))
            throw new RuntimeMappingException("RUNTIME_ORDER_CONTRACT_INVALID", rule.id(), "orderProjection");
    }
}
