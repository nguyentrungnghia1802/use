package org.vnu.sme.goal.verify.istar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.vnu.sme.goal.dsl.istar.mm.Actor;
import org.vnu.sme.goal.dsl.istar.mm.AndRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Dependency;
import org.vnu.sme.goal.dsl.istar.mm.Goal;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.dsl.istar.mm.IntentionalElement;
import org.vnu.sme.goal.dsl.istar.mm.OrRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Refinement;
import org.vnu.sme.goal.dsl.istar.mm.Task;

/** Immutable Goal and Task markings, with their temporal and propagation rules. */
public final class IStarMarking {
    private static final int MAX_SATURATION_STEPS = 10_000;
    private static final int MAX_DEPENDENCY_STEPS = 10_000;

    private final Map<String, GoalMarking> goals;
    private final Map<String, TaskMarking> tasks;

    private IStarMarking(Map<String, GoalMarking> goals, Map<String, TaskMarking> tasks) {
        this.goals = goals;
        this.tasks = tasks;
    }

    /** Initial marking m0: Goal and Task occurrences are all unknown. */
    public static IStarMarking initial(GoalModel gm) {
        Map<String, GoalMarking> goals = new LinkedHashMap<>();
        Map<String, TaskMarking> tasks = new LinkedHashMap<>();
        for (IntentionalElement e : gm.allElements().values()) {
            switch (e) {
                case Goal g -> goals.put(g.id(), GoalMarking.initial(g.goalType()));
                case Task t -> tasks.put(t.id(), TaskMarking.initial());
                default -> { }
            }
        }
        return new IStarMarking(goals, tasks);
    }

    public GoalTaskStatus goalTaskStatus(String id) {
        GoalMarking goal = goals.get(id);
        if (goal != null) return goal.status();
        TaskMarking task = tasks.get(id);
        return task == null ? GoalTaskStatus.UNKNOWN : task.status();
    }

    public IStarMarking withGoalTask(String id, GoalTaskStatus s) {
        if (goals.containsKey(id)) {
            Map<String, GoalMarking> values = new LinkedHashMap<>(goals);
            values.put(id, GoalMarking.fromStatus(goals.get(id).type(), s));
            return new IStarMarking(values, tasks);
        }
        if (tasks.containsKey(id)) {
            Map<String, TaskMarking> values = new LinkedHashMap<>(tasks);
            values.put(id, TaskMarking.fromStatus(s));
            return new IStarMarking(goals, values);
        }
        return this;
    }

    public GoalMarking goalMarking(String id) { return goals.get(id); }
    public TaskMarking taskMarking(String id) { return tasks.get(id); }

    public IStarMarking withGoalMarking(String id, GoalMarking value) {
        Map<String, GoalMarking> values = new LinkedHashMap<>(goals);
        values.put(id, value);
        return new IStarMarking(values, tasks);
    }

    public IStarMarking withTaskMarking(String id, TaskMarking value) {
        Map<String, TaskMarking> values = new LinkedHashMap<>(tasks);
        values.put(id, value);
        return new IStarMarking(goals, values);
    }

    public Map<String, GoalTaskStatus> goalTaskStatuses() {
        Map<String, GoalTaskStatus> result = new LinkedHashMap<>();
        goals.forEach((id, value) -> result.put(id, value.status()));
        tasks.forEach((id, value) -> result.put(id, value.status()));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, GoalMarking> goalMarkings() { return Collections.unmodifiableMap(goals); }
    public Map<String, TaskMarking> taskMarkings() { return Collections.unmodifiableMap(tasks); }

    @Override
    public boolean equals(Object o) {
        return o instanceof IStarMarking m && goals.equals(m.goals) && tasks.equals(m.tasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goals, tasks);
    }

    @Override
    public String toString() {
        return "IStarMarking" + goalTaskStatuses();
    }

    /** Fires the leaf element {@code firedLeafId} then propagates refinement. */
    public static IStarMarking fire(GoalModel gm, IStarMarking m, String firedLeafId) {
        return assignGoalTask(gm, m, firedLeafId, GoalTaskStatus.FULFILLED);
    }

    /** Assigns a Goal or Task status and propagates refinement. */
    public static IStarMarking assignGoalTask(GoalModel model, IStarMarking marking, String id,
                                              GoalTaskStatus status) {
        return saturate(model, marking.withGoalTask(id, status));
    }

    /**
     * Recomputes non-leaf Goal/Task statuses from the current refinement tree, including the
     * negative case that the operational {@link #saturate} intentionally does not derive:
     * an AND parent is pending when any child is pending, and an OR parent is pending when no
     * child is fulfilled but at least one child is pending.
     */
    public static IStarMarking closePending(GoalModel gm, IStarMarking m) {
        Map<String, List<String>> andGroups = new LinkedHashMap<>();
        Map<String, Set<String>> orGroups = new LinkedHashMap<>();
        collectRefinements(gm, andGroups, orGroups);

        IStarMarking current = m;
        boolean changed = true;
        int steps = 0;
        while (changed && steps++ < MAX_SATURATION_STEPS) {
            changed = false;

            for (Map.Entry<String, List<String>> e : andGroups.entrySet()) {
                if (hasDirectGoalCondition(gm, e.getKey())) continue;
                GoalTaskStatus status = statusOfAnd(current, e.getValue());
                if (status != GoalTaskStatus.UNKNOWN && current.goalTaskStatus(e.getKey()) != status) {
                    current = current.withGoalTask(e.getKey(), status);
                    changed = true;
                }
            }

            for (Map.Entry<String, Set<String>> e : orGroups.entrySet()) {
                if (hasDirectGoalCondition(gm, e.getKey())) continue;
                GoalTaskStatus status = statusOfOr(current, e.getValue());
                if (status != GoalTaskStatus.UNKNOWN && current.goalTaskStatus(e.getKey()) != status) {
                    current = current.withGoalTask(e.getKey(), status);
                    changed = true;
                }
            }
        }
        return current;
    }

    private static IStarMarking saturate(GoalModel gm, IStarMarking m) {
        Map<String, List<String>> andGroups = new LinkedHashMap<>();
        Map<String, Set<String>> orGroups = new LinkedHashMap<>();
        collectRefinements(gm, andGroups, orGroups);

        IStarMarking current = m;
        boolean changed = true;
        int steps = 0;
        while (changed && steps++ < MAX_SATURATION_STEPS) {
            changed = false;

            // P_AND - parent satisfied once every child is fulfilled.
            for (Map.Entry<String, List<String>> e : andGroups.entrySet()) {
                String parent = e.getKey();
                if (hasDirectGoalCondition(gm, parent)) continue;
                if (current.goalTaskStatus(parent) == GoalTaskStatus.FULFILLED) continue;
                boolean allChildrenFulfilled = true;
                for (String child : e.getValue()) {
                    if (current.goalTaskStatus(child) != GoalTaskStatus.FULFILLED) {
                        allChildrenFulfilled = false;
                        break;
                    }
                }
                if (allChildrenFulfilled) {
                    current = current.withGoalTask(parent, GoalTaskStatus.FULFILLED);
                    changed = true;
                }
            }

            // P_OR - parent satisfied as soon as at least one child is fulfilled.
            for (Map.Entry<String, Set<String>> e : orGroups.entrySet()) {
                String parent = e.getKey();
                if (hasDirectGoalCondition(gm, parent)) continue;
                if (current.goalTaskStatus(parent) == GoalTaskStatus.FULFILLED) continue;
                boolean anyChildFulfilled = false;
                for (String child : e.getValue()) {
                    if (current.goalTaskStatus(child) == GoalTaskStatus.FULFILLED) {
                        anyChildFulfilled = true;
                        break;
                    }
                }
                if (anyChildFulfilled) {
                    current = current.withGoalTask(parent, GoalTaskStatus.FULFILLED);
                    changed = true;
                }
            }
        }
        return current;
    }

    private static void collectRefinements(GoalModel gm, Map<String, List<String>> andGroups,
                                           Map<String, Set<String>> orGroups) {
        for (Actor actor : gm.getActors()) {
            for (Refinement r : actor.refinements()) {
                switch (r) {
                    case AndRefinement and -> andGroups
                            .computeIfAbsent(and.parent(), k -> new ArrayList<>())
                            .addAll(and.children());
                    case OrRefinement or -> orGroups
                            .computeIfAbsent(or.parent(), k -> new LinkedHashSet<>())
                            .add(or.child());
                }
            }
        }
    }

    private static GoalTaskStatus statusOfAnd(IStarMarking marking, List<String> children) {
        if (children.isEmpty()) return GoalTaskStatus.UNKNOWN;
        boolean allFulfilled = true;
        boolean anyPending = false;
        for (String child : children) {
            GoalTaskStatus childStatus = marking.goalTaskStatus(child);
            if (childStatus == GoalTaskStatus.VIOLATED) return GoalTaskStatus.VIOLATED;
            if (childStatus != GoalTaskStatus.FULFILLED) allFulfilled = false;
            if (childStatus == GoalTaskStatus.PENDING) anyPending = true;
        }
        if (allFulfilled) return GoalTaskStatus.FULFILLED;
        return anyPending ? GoalTaskStatus.PENDING : GoalTaskStatus.UNKNOWN;
    }

    /** A declared condition is the authoritative predicate P of (A,P,S). */
    private static boolean hasDirectGoalCondition(GoalModel model, String id) {
        return model.findElement(id).filter(Goal.class::isInstance)
                .map(Goal.class::cast)
                .map(goal -> !goal.conditions().isEmpty()).orElse(false);
    }

    private static GoalTaskStatus statusOfOr(IStarMarking marking, Set<String> children) {
        if (children.isEmpty()) return GoalTaskStatus.UNKNOWN;
        boolean anyPending = false;
        boolean anyKnown = false;
        boolean allKnownViolated = true;
        for (String child : children) {
            GoalTaskStatus childStatus = marking.goalTaskStatus(child);
            if (childStatus == GoalTaskStatus.FULFILLED) return GoalTaskStatus.FULFILLED;
            if (childStatus == GoalTaskStatus.PENDING) anyPending = true;
            if (childStatus != GoalTaskStatus.UNKNOWN) anyKnown = true;
            if (childStatus != GoalTaskStatus.UNKNOWN && childStatus != GoalTaskStatus.VIOLATED) {
                allKnownViolated = false;
            }
        }
        if (anyPending) return GoalTaskStatus.PENDING;
        return anyKnown && allKnownViolated ? GoalTaskStatus.VIOLATED : GoalTaskStatus.UNKNOWN;
    }

    /** Propagates fulfillment along Goal and Task dependency endpoints. */
    public static IStarMarking propagateDependencies(GoalModel model, IStarMarking marking) {
        IStarMarking current = marking;
        boolean changed = true;
        int steps = 0;
        while (changed && steps++ < MAX_DEPENDENCY_STEPS) {
            changed = false;
            for (Dependency d : model.getDependencies()) {
                if (d.dependerElmt() == null || d.dependeeElmt() == null) continue;
                if (!current.goals.containsKey(d.dependerElmt()) && !current.tasks.containsKey(d.dependerElmt())) continue;
                if (current.goalTaskStatus(d.dependerElmt()) == GoalTaskStatus.FULFILLED) continue;
                if (current.goalTaskStatus(d.dependeeElmt()) == GoalTaskStatus.FULFILLED) {
                    current = assignGoalTask(model, current, d.dependerElmt(), GoalTaskStatus.FULFILLED);
                    changed = true;
                }
            }
        }
        return current;
    }

    public enum GoalTaskStatus { UNKNOWN, PENDING, FULFILLED, VIOLATED }

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
}
