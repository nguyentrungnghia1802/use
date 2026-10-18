package org.vnu.sme.goal.verify.conformance.semantics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.vnu.sme.goal.dsl.istar.mm.Goal;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.IntentionalElement;
import org.vnu.sme.goal.dsl.istar.mm.Quality;
import org.vnu.sme.goal.dsl.istar.mm.Resource;
import org.vnu.sme.goal.dsl.istar.mm.Task;

/**
 * Goal Model Marking — Definition 3.1 (Caballero-Villalobos), instantiated over the actual
 * {@link GoalModel} of this project. Immutable: every update returns a new instance, so a
 * marking can safely be used as a state / map key while building the product LTS.
 */
public final class IStarMarking {

    private final Map<String, GoalMarking> goals;
    private final Map<String, TaskMarking> tasks;
    private final Map<String, QualityStatus> quality;

    private IStarMarking(Map<String, GoalMarking> goals, Map<String, TaskMarking> tasks,
                         Map<String, QualityStatus> quality) {
        this.goals = goals;
        this.tasks = tasks;
        this.quality = quality;
    }

    /** Initial marking m0: Goal/Task and Quality occurrences are all unknown. */
    public static IStarMarking initial(GoalModel gm) {
        Map<String, GoalMarking> goals = new LinkedHashMap<>();
        Map<String, TaskMarking> tasks = new LinkedHashMap<>();
        Map<String, QualityStatus> q = new LinkedHashMap<>();
        for (IntentionalElement e : gm.allElements().values()) {
            switch (e) {
                case Goal g -> goals.put(g.id(), GoalMarking.initial(g.goalType()));
                case Task t -> tasks.put(t.id(), TaskMarking.initial());
                case Quality qu -> q.put(qu.id(), QualityStatus.UNKNOWN);
                case Resource r -> { /* resources are not part of the marking */ }
            }
        }
        return new IStarMarking(goals, tasks, q);
    }

    public GoalTaskStatus goalTaskStatus(String id) {
        GoalMarking goal = goals.get(id);
        if (goal != null) return goal.status();
        TaskMarking task = tasks.get(id);
        return task == null ? GoalTaskStatus.UNKNOWN : task.status();
    }

    public QualityStatus qualityStatus(String id) {
        return quality.getOrDefault(id, QualityStatus.UNKNOWN);
    }

    public IStarMarking withGoalTask(String id, GoalTaskStatus s) {
        if (goals.containsKey(id)) {
            Map<String, GoalMarking> values = new LinkedHashMap<>(goals);
            values.put(id, GoalMarking.fromStatus(goals.get(id).type(), s));
            return new IStarMarking(values, tasks, quality);
        }
        if (tasks.containsKey(id)) {
            Map<String, TaskMarking> values = new LinkedHashMap<>(tasks);
            values.put(id, TaskMarking.fromStatus(s));
            return new IStarMarking(goals, values, quality);
        }
        return this;
    }

    public GoalMarking goalMarking(String id) { return goals.get(id); }
    public TaskMarking taskMarking(String id) { return tasks.get(id); }

    public IStarMarking withGoalMarking(String id, GoalMarking value) {
        Map<String, GoalMarking> values = new LinkedHashMap<>(goals);
        values.put(id, value);
        return new IStarMarking(values, tasks, quality);
    }

    public IStarMarking withTaskMarking(String id, TaskMarking value) {
        Map<String, TaskMarking> values = new LinkedHashMap<>(tasks);
        values.put(id, value);
        return new IStarMarking(goals, values, quality);
    }

    public IStarMarking withQuality(String id, QualityStatus s) {
        Map<String, QualityStatus> q = new LinkedHashMap<>(quality);
        q.put(id, s);
        return new IStarMarking(goals, tasks, q);
    }

    /** Membership test for F_C (Definition 4.1): every declared quality must be TRUE. */
    public boolean isSuccess() {
        return quality.values().stream().allMatch(v -> v == QualityStatus.TRUE);
    }

    public Map<String, GoalTaskStatus> goalTaskStatuses() {
        Map<String, GoalTaskStatus> result = new LinkedHashMap<>();
        goals.forEach((id, value) -> result.put(id, value.status()));
        tasks.forEach((id, value) -> result.put(id, value.status()));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, GoalMarking> goalMarkings() { return Collections.unmodifiableMap(goals); }
    public Map<String, TaskMarking> taskMarkings() { return Collections.unmodifiableMap(tasks); }

    public Map<String, QualityStatus> qualityStatuses() {
        return Collections.unmodifiableMap(quality);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IStarMarking m && goals.equals(m.goals) && tasks.equals(m.tasks)
                && quality.equals(m.quality);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goals, tasks, quality);
    }

    @Override
    public String toString() {
        return "IStarMarking" + goalTaskStatuses() + quality;
    }
}
