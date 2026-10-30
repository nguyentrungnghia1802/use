package org.tzi.use.semantic.engine;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.tzi.use.semantic.engine.runtime.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;
import static org.junit.jupiter.api.Assertions.*;

class SingleOwnerContextTopologyTest {
    @Test void singleOwnerBoundIsLinearWhileOriginalCrossProductGuardRemainsStrict() {
        var model=new GoalModel("IndependentDelegations");var cast=new ArrayList<ScenarioInstance>();
        for(int i=0;i<12;i++) {
            model.addActor(new Role("Role"+i,List.of(new Goal("goal"+i,GoalType.ACHIEVE)),List.of(),List.of(),List.of(),List.of(),List.of()));
            cast.add(new ScenarioInstance("actor"+i,"Role"+i));
        }
        model.addDependency(new Dependency("Role0","goal0",new Goal("delegation",GoalType.ACHIEVE),"Role1","goal1"));
        var admitted=RuntimeModelAdmission.admit(model,cast,(a,x,b,y)->true,profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1);
        assertEquals(12,admitted.occurrenceUpperBound());
        assertThrows(org.tzi.use.semantic.engine.api.EngineAdmissionException.class,()->RuntimeModelAdmission.admit(model,cast,(a,x,b,y)->true,profile),"Original topology must retain its conservative cross-product bound");
        try(var session=new Use4GoalRuntimeSessionAdapter(model,cast,(a,x,b,y)->true,"linear",profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {assertEquals(12,session.topology().occurrences().size());}
        var ambiguous=new ArrayList<>(cast);ambiguous.add(new ScenarioInstance("duplicate","Role2"));
        assertThrows(org.tzi.use.semantic.engine.api.EngineAdmissionException.class,()->RuntimeModelAdmission.admit(model,ambiguous,(a,x,b,y)->true,profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1));
        var oversized=new GoalModel("Oversized");var nodes=new ArrayList<IntentionalElement>();
        for(int i=0;i<4097;i++)nodes.add(new Goal("g"+i,GoalType.ACHIEVE));
        oversized.addActor(new Role("Many",nodes,List.of(),List.of(),List.of(),List.of(),List.of()));
        assertThrows(org.tzi.use.semantic.engine.api.EngineAdmissionException.class,()->RuntimeModelAdmission.admit(oversized,List.of(new ScenarioInstance("many","Many")),(a,x,b,y)->true,profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1));
    }
    private final EngineSemanticsProfile profile=EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1;
    private GoalModel model(){return new IStarFrontend().compile("istar Context { role R { goal root : Achieve goal receiver : Achieve > root goal sibling : Achieve > root condition {[ true ]} } role S { goal work : Achieve task x > work pre {[ true ]} post {[ true ]} task y > work pre {[ true ]} post {[ true ]} } depend R.receiver -> goal request -> S.work }","context.istar",profile).requireModel();}
    private List<ScenarioInstance> cast(){return List.of(new ScenarioInstance("one","R"),new ScenarioInstance("two","S"));}
    @Test void bijectionConnectsOriginalRefinementAndDependencyWithoutChangingTheDefaultTopology() {
        var model=model();String hash=org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(model);
        for(var policy:EngineTopologyPolicy.values())try(var session=new Use4GoalRuntimeSessionAdapter(model,cast(),(a,x,b,y)->true,"one-proved-context",profile,policy)) {
            var tasks=new TreeMap<String,EngineInputs.TaskInput>();var goals=new TreeMap<String,EngineInputs.GoalInput>();
            session.topology().occurrences().forEach((id,o)->{if(model.findElement(o.sourceElementId()).orElseThrow() instanceof Task)tasks.put(id,new EngineInputs.TaskInput("episode",true,true,true));else if(o.sourceElementId().equals("sibling"))goals.put(id,new EngineInputs.GoalInput("episode",true,true));});
            var result=session.step(new EngineInputs(goals,tasks,Map.of()));
            assertTrue(result.originalApiCalls().containsAll(List.of("TaskMarking.update","GoalMarking.update","IStarPropagation.closePending","GoalModelInstanceTranslator.propagateDependencies")));
            var roots=session.topology().occurrences().values().stream().filter(o->o.sourceElementId().equals("root")).toList();assertEquals(1,roots.size());
            assertEquals(policy==EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1?GoalTaskStatus.FULFILLED:GoalTaskStatus.UNKNOWN,result.aggregate().goalTaskStatus(roots.getFirst().occurrenceId()));
            if(policy==EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)assertEquals(model.allElements().size(),session.topology().occurrences().size());
        }
        assertEquals(hash,org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(model));
    }
    @Test void uniqueCompleteContextIsMandatoryAndPoliciesCannotShareHistory() {
        var model=model();assertThrows(IllegalArgumentException.class,()->new Use4GoalRuntimeSessionAdapter(model,List.of(new ScenarioInstance("one","R"),new ScenarioInstance("again","R"),new ScenarioInstance("two","S")),(a,x,b,y)->true,"c",profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1));
        assertThrows(IllegalArgumentException.class,()->new Use4GoalRuntimeSessionAdapter(model,cast(),(a,x,b,y)->a.equals(b),"c",profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1));
        try(var original=new Use4GoalRuntimeSessionAdapter(model,cast(),(a,x,b,y)->true,"c",profile);var selected=new Use4GoalRuntimeSessionAdapter(model,cast(),(a,x,b,y)->true,"c",profile,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            assertNotEquals(original.checkpoint().definitionHash(),selected.checkpoint().definitionHash());assertThrows(IllegalArgumentException.class,()->selected.seedCompatibleLeafHistory(original,Map.of()));
        }
    }
}
