package org.vnu.sme.goal.verify.istar.marking;

import org.vnu.sme.goal.verify.istar.temporal.GoalTemporalEvaluator;
import org.vnu.sme.goal.dsl.istar.mm.GoalType;

/** Runtime marking (A,P,S) of one Goal occurrence. */
public record GoalMarking(GoalType type, boolean active, boolean condition, boolean stable) {
    public GoalMarking {
        if (type == null) type = GoalType.ACHIEVE;
    }

    public static GoalMarking initial(GoalType type) {
        return new GoalMarking(type, false, false, true);
    }

    /** Starts a fresh activation episode and consumes the current CSL predicate. */
    public GoalMarking update(boolean nextActive, boolean predicate) {
        return GoalTemporalEvaluator.update(this, nextActive, predicate);
    }

    public GoalTaskStatus status() {
        return GoalTemporalEvaluator.status(this);
    }

    public static GoalMarking fromStatus(GoalType type, GoalTaskStatus status) {
        return switch (status) {
            case UNKNOWN -> initial(type);
            case PENDING -> new GoalMarking(type, true, false, true);
            case FULFILLED -> new GoalMarking(type, true, true, true);
            case VIOLATED -> new GoalMarking(type, true, false, false);
        };
    }
}
