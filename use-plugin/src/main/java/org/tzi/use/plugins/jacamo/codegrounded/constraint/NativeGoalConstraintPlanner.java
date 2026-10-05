package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.List;
import org.jacamo.bridge.contract.semantic.Fidelity;

/** Structural context rules and the documented Scheme.getCommittedAgents/Mission.getGoals relation. */
public final class NativeGoalConstraintPlanner {
    public List<NativeConstraintSpec> plan() {
        return List.of(
            rule("GoalDecompositionContext","OrganizationalGoal","self.subGoals->forAll(g | g.schemeSpecSemanticId = self.schemeSpecSemanticId and g.schemeInstanceIdentity = self.schemeInstanceIdentity)",false),
            rule("GoalAcyclic","OrganizationalGoal","self.subGoals->closure(g | g.subGoals)->excludes(self)",false),
            rule("GoalSequenceOrdinals","OrganizationalGoal","self.decompositionOperator = 'sequence' implies (self.subGoals->isUnique(g | g.orderInParent) and self.subGoals->forAll(g | not g.orderInParent.oclIsUndefined() and g.orderInParent >= 0 and g.orderInParent < self.subGoals->size()))",false),
            rule("MissionGoalContext","Mission","self.missionGoals->forAll(g | g.schemeSpecSemanticId = self.schemeSpecSemanticId and g.schemeInstanceIdentity = self.schemeInstanceIdentity)",false),
            rule("SchemeGoalContext","Scheme","self.schemeGoals->forAll(g | g.schemeSpecSemanticId = self.specSemanticId and g.schemeInstanceIdentity = self.semanticId)",false),
            rule("GoalStateContext","OrganizationalGoal","not self.runtimeState.oclIsUndefined() implies (self.goalScheme->size() = 1 and self.schemeInstanceIdentity = self.goalScheme.semanticId)",true),
            rule("GoalCommittedResponsibility","OrganizationalGoal","self.stateEvidence = 'OFFICIAL_SCHEME_BOARD_OBSERVABLE_V1' implies self.goalCommittedAgents->asSet() = self.referencingMissions.committedAgents->asSet()",true));
    }
    private NativeConstraintSpec rule(String name,String context,String body,boolean runtime) {
        return new NativeConstraintSpec(name,context,body,List.of("M11","M12","M13","M35","M38"),
                runtime?List.of("runtime.moise.goalState.v1"):List.of(),Fidelity.EXACT,"CORE:GOAL:"+name,ConstraintMigrationStatus.NATIVE);
    }
}
