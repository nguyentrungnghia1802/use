package org.tzi.use.semantic.engine.runtime;

import java.util.*;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTopology.ActorBinding;

/** Complete structured occurrence identity within a run/program owner, including its activation episode. */
public record EngineOccurrenceKey(String sourceElementId,List<ActorBinding> bindingStack,String episode) {
    public EngineOccurrenceKey {
        Objects.requireNonNull(sourceElementId);bindingStack=List.copyOf(bindingStack);
        if(episode==null||episode.isBlank())throw new IllegalArgumentException("EXPLICIT_EPISODE_REQUIRED");
    }
}
