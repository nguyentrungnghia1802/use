package org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser;

import java.util.*;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.mm.GoalModelInstance;

/** P-CTX-01 new API, not an API claimed to exist in the pinned upstream. */
public record GoalModelInstanceTopology(GoalModelInstance instance,
        Map<String,OccurrenceBinding> occurrences,Map<String,String> actorLabels,Map<String,String> nodeLabels) {
    public record ActorBinding(String actorType,String instanceId) { }
    public record OccurrenceBinding(String occurrenceId,String sourceElementId,String ownerActorId,List<ActorBinding> stack) {
        public OccurrenceBinding {stack=List.copyOf(stack);}
    }
    public GoalModelInstanceTopology {
        occurrences=Collections.unmodifiableMap(new LinkedHashMap<>(occurrences));
        actorLabels=Map.copyOf(actorLabels);nodeLabels=Map.copyOf(nodeLabels);
    }
}
