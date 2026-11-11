package org.vnu.sme.goal.verify.conformance.semantics;

/** Runtime marking (Q,R) of one Task occurrence. */
public record TaskMarking(boolean preSeen, boolean postSeen) {
    public static TaskMarking initial() { return new TaskMarking(false, false); }

    public TaskMarking update(boolean required, boolean pre, boolean post) {
        if (!required) return this;
        boolean nextQ = preSeen || pre;
        boolean nextR = postSeen || (nextQ && post);
        return new TaskMarking(nextQ, nextR);
    }

    public GoalTaskStatus status() {
        if (postSeen) return GoalTaskStatus.FULFILLED;
        return preSeen ? GoalTaskStatus.PENDING : GoalTaskStatus.UNKNOWN;
    }

    public static TaskMarking fromStatus(GoalTaskStatus status) {
        return switch (status) {
            case UNKNOWN -> initial();
            case PENDING -> new TaskMarking(true, false);
            case FULFILLED -> new TaskMarking(true, true);
            case VIOLATED -> throw new IllegalArgumentException("a Task cannot be VIOLATED");
        };
    }
}
