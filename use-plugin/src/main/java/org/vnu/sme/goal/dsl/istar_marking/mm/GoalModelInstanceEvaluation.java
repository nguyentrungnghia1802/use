package org.vnu.sme.goal.dsl.istar_marking.mm;

import java.util.List;
import java.util.Map;

import org.vnu.sme.goal.dsl.istar_marking.mm.IStarMarking;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar_marking.mm.AggregateResult;
import org.vnu.sme.goal.dsl.istar_marking.mm.GoalModelInstance;

public record GoalModelInstanceEvaluation(GoalModelInstance goalModelInstance,
                                      IStarMarking instanceMarking,
                                      List<AggregateResult> aggregates,
                                      Map<String, String> actorLabels,
                                      Map<String, String> nodeLabels,
                                      Map<String, String> elementScopeActorType) {
    public GoalModelInstanceEvaluation {
        aggregates = List.copyOf(aggregates);
        actorLabels = Map.copyOf(actorLabels);
        nodeLabels = Map.copyOf(nodeLabels);
        elementScopeActorType = Map.copyOf(elementScopeActorType);
    }

    public GoalModel instanceModel() {
        return goalModelInstance.model();
    }
}
