package org.tzi.use.plugins.jacamo.verify.istar;

import org.vnu.sme.goal.verify.istar.IStarMarking.TaskMarking;
import org.vnu.sme.goal.verify.istar.IStarMarking.GoalMarking;
import org.vnu.sme.goal.dsl.istar_marking.mm.GoalModelInstanceTopology.ActorBinding;
import java.util.*;

/** Immutable staged history only. No native model/state, socket, or UI ownership. */
public record EvaluationHistory(String ownerToken,long epoch,String definitionHash,String castRevision,long boundary,
        Map<String,GoalMarking> goals,Map<String,TaskMarking> tasks,
        Map<String,OccurrenceKey> keys) {
    public EvaluationHistory {goals=Map.copyOf(goals);tasks=Map.copyOf(tasks);keys=Map.copyOf(keys);}


    /** Complete structured occurrence identity within a run/program owner, including its activation episode. */
    public record OccurrenceKey(String sourceElementId,List<ActorBinding> bindingStack,String episode) {
        public OccurrenceKey {
            Objects.requireNonNull(sourceElementId);bindingStack=List.copyOf(bindingStack);
            if(episode==null||episode.isBlank())throw new IllegalArgumentException("EXPLICIT_EPISODE_REQUIRED");
        }
    }
}
