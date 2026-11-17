package org.vnu.sme.goal.verify.istar.propagation;

import org.vnu.sme.goal.dsl.istar.mm.Dependency;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.verify.istar.marking.GoalTaskStatus;
import org.vnu.sme.goal.verify.istar.marking.IStarMarking;

/** Existing dependency fixpoint, shared by scenario and observed-state evaluation. */
public final class IStarDependencyPropagation {
    private static final int MAX_DEPENDENCY_STEPS = 10_000;

    private IStarDependencyPropagation() {}

    public static IStarMarking propagate(GoalModel model, IStarMarking marking) {
        IStarMarking current = marking;
        boolean changed = true;
        int steps = 0;
        while (changed && steps++ < MAX_DEPENDENCY_STEPS) {
            changed = false;
            for (Dependency d : model.getDependencies()) {
                if (d.dependerElmt() == null || d.dependeeElmt() == null) continue;
                if (current.goalTaskStatus(d.dependerElmt()) == GoalTaskStatus.FULFILLED) continue;
                if (current.goalTaskStatus(d.dependeeElmt()) == GoalTaskStatus.FULFILLED) {
                    current = IStarPropagation.assignGoalTask(model, current, d.dependerElmt(), GoalTaskStatus.FULFILLED);
                    changed = true;
                }
            }
        }
        return current;
    }
}
