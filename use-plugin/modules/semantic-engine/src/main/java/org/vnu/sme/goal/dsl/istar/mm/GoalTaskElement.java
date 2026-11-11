package org.vnu.sme.goal.dsl.istar.mm;

/**
 * The subset of {@link IntentionalElement} that Refinement (refines/to)
 * targets — Resource and Quality are excluded.
 */
public sealed interface GoalTaskElement extends ConcreteIntentionalElement
        permits Goal, Task {
    java.util.List<IStarOclConstraint> constraints();
    default java.util.List<IStarOclConstraint> preconditions() {
        return java.util.List.of();
    }
    default java.util.List<IStarOclConstraint> postconditions() {
        return java.util.List.of();
    }
    default String oclSource() {
        return postconditions().stream().map(x -> "(" + x.oclBody() + ")")
                .reduce((a, b) -> a + " and " + b).orElse(null);
    }
}
