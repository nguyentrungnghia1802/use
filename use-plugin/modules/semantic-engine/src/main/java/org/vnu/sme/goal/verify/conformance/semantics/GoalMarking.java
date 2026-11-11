package org.vnu.sme.goal.verify.conformance.semantics;

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
        if (!nextActive) return initial(type);
        boolean entering = !active;
        boolean previousP = entering ? baseline(type) : condition;
        boolean nextP = type == GoalType.ACHIEVE
                ? ((!entering && condition) || predicate)
                : predicate;
        boolean previousS = entering ? true : stable;
        boolean nextS = previousS && (!previousP || nextP);
        return new GoalMarking(type, true, nextP, nextS);
    }

    public GoalTaskStatus status() {
        if (!active) return GoalTaskStatus.UNKNOWN;
        return switch (type) {
            case ACHIEVE, NONE -> condition ? GoalTaskStatus.FULFILLED : GoalTaskStatus.PENDING;
            case MAINTAIN -> stable && condition
                    ? GoalTaskStatus.FULFILLED : GoalTaskStatus.VIOLATED;
            case SUSTAIN -> !stable ? GoalTaskStatus.VIOLATED
                    : condition ? GoalTaskStatus.FULFILLED : GoalTaskStatus.PENDING;
        };
    }

    public static GoalMarking fromStatus(GoalType type, GoalTaskStatus status) {
        return switch (status) {
            case UNKNOWN -> initial(type);
            case PENDING -> new GoalMarking(type, true, false, true);
            case FULFILLED -> new GoalMarking(type, true, true, true);
            case VIOLATED -> new GoalMarking(type, true, false, false);
        };
    }

    private static boolean baseline(GoalType type) {
        return type == GoalType.MAINTAIN;
    }
}
