package org.tzi.use.plugins.jacamo.mapping;

/** Exact typed source-navigation binding; callers supply an already validated OCL receiver expression. */
public final class OrderNavigationBinding {
    public String bind(TransformationPlan plan, String sourceIdentity, String receiver) {
        var matches = plan.orderProjections().stream().filter(p -> p.sourceIdentity().equals(sourceIdentity)).toList();
        if (matches.size() != 1) throw new MappingException("ORDER_NAVIGATION_UNRESOLVED", sourceIdentity);
        return "(" + receiver + ")." + matches.getFirst().query() + "()";
    }
}
