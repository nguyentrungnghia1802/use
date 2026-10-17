package org.tzi.use.plugins.jacamo.semantics.istar;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.observer.*;

/** Explicit selection of board acknowledgement as a requirement predicate. Source type is never cast to a temporal type. */
public final class MoiseRequirementBinder {
    public RequirementModel.Node bind(String occurrence,String requirement,RequirementModel.Temporal temporal,
            RequirementModel.Binding actorScope,FunctionalObservation cut,String sourceGoal) {
        var goal=cut.goals().stream().filter(g->g.definition().equals(sourceGoal)).findFirst().orElseThrow(()->new IllegalArgumentException("MOISE_REQUIREMENT_GOAL_UNRESOLVED"));
        var binding=new RequirementModel.Binding(actorScope.actorId(),actorScope.playerIdentity(),actorScope.roleTypeId(),actorScope.contextIdentity(),
            FunctionalObservation.goalIdentity(cut.schemeIdentity(),sourceGoal,goal.arguments()),"observer:"+FunctionalUseBinding.GOAL,actorScope.episodeId());
        return new RequirementModel.Node(occurrence,requirement,RequirementModel.Kind.GOAL,temporal,binding,"self.state = 'SATISFIED'","true",RequirementModel.Refinement.NONE,List.of(),"","");
    }
}
