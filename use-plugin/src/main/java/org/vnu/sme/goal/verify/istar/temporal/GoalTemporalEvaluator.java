package org.vnu.sme.goal.verify.istar.temporal;

import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.verify.istar.marking.GoalMarking;
import org.vnu.sme.goal.verify.istar.marking.GoalTaskStatus;

/** Existing activation-episode recurrence; does not read runtime or USE state. */
public final class GoalTemporalEvaluator {
    private GoalTemporalEvaluator() {}

    public static GoalMarking update(GoalMarking marking, boolean nextActive, boolean predicate) {
        GoalType type = marking.type();
        boolean active = marking.active();
        boolean condition = marking.condition();
        boolean stable = marking.stable();
        if (!nextActive) return GoalMarking.initial(type);
        boolean entering = !active;
        boolean previousP = entering ? baseline(type) : condition;
        boolean nextP = type == GoalType.ACHIEVE
                ? ((!entering && condition) || predicate)
                : predicate;
        boolean previousS = entering ? true : stable;
        boolean nextS = previousS && (!previousP || nextP);
        return new GoalMarking(type, true, nextP, nextS);
    }

    public static GoalTaskStatus status(GoalMarking marking) {
        GoalType type = marking.type();
        boolean active = marking.active();
        boolean condition = marking.condition();
        boolean stable = marking.stable();
        if (!active) return GoalTaskStatus.UNKNOWN;
        return switch (type) {
            case ACHIEVE, NONE -> condition ? GoalTaskStatus.FULFILLED : GoalTaskStatus.PENDING;
            case MAINTAIN -> stable && condition
                    ? GoalTaskStatus.FULFILLED : GoalTaskStatus.VIOLATED;
            case SUSTAIN -> !stable ? GoalTaskStatus.VIOLATED
                    : condition ? GoalTaskStatus.FULFILLED : GoalTaskStatus.PENDING;
        };
    }

    private static boolean baseline(GoalType type) {
        return type == GoalType.MAINTAIN;
    }
}
