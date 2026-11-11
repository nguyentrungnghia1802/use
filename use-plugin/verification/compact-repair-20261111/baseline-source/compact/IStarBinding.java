package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolved, typed iStar correspondence binding in the compiled observation plan.
 */
public record IStarBinding(
        SourceReference sourceRef,
        TargetReference targetRef,
        SourceSymbol sourceSymbol,
        TargetSymbol targetSymbol,
        BindingKind kind,
        Optional<StepRange> stepRange) {

    public enum BindingKind {
        ACTOR_MAPPING,
        GOAL_MAPPING,
        TASK_MAPPING,
        RESOURCE_MAPPING
    }

    public record StepRange(int first, int after) {
        public StepRange {
            if (after <= first) throw new IllegalArgumentException("Invalid step range: first=" + first + ", after=" + after);
        }
    }

    public IStarBinding {
        Objects.requireNonNull(sourceRef, "sourceRef");
        Objects.requireNonNull(targetRef, "targetRef");
        Objects.requireNonNull(sourceSymbol, "sourceSymbol");
        Objects.requireNonNull(targetSymbol, "targetSymbol");
        Objects.requireNonNull(kind, "kind");
        stepRange = Objects.requireNonNullElse(stepRange, Optional.empty());
    }

    public boolean isTask() { return kind == BindingKind.TASK_MAPPING; }
    public boolean isGoal() { return kind == BindingKind.GOAL_MAPPING; }
    public SourceReference sourceReference() { return sourceRef; }
    public TargetReference targetReference() { return targetRef; }
}

