package org.tzi.use.semantic.engine;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.tzi.use.semantic.engine.runtime.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;
import static org.junit.jupiter.api.Assertions.*;

class RoleSpecializationPolicyTest {
    private static EngineSemanticsProfile profile(){return EngineSemanticsProfile.valueOf("ROLE_SPECIALIZATION_V1");}
    private static final String SOURCE="""
        istar Specialized {
          agent Team { goal requested : Achieve }
          role Capability { }
          role Performer {
            Performer is-a Capability
            goal delivered : Achieve
            task action > delivered pre {[ true ]} post {[ true ]}
          }
          depend Team.requested -> goal promise -> Performer.delivered
        }
        """;
    private static GoalModel model(String source){return new IStarFrontend().compile(source,"role-specialization.istar",profile()).requireModel();}
    @Test void explicitVersionAdmitsUnaryRealizationButExistingProfilesStayStrict() {
        for(var old:List.of(EngineSemanticsProfile.USE4GOAL_OBSERVED_V1,EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1))
            assertFalse(new IStarFrontend().compile(SOURCE,"old",old).ok());
        var goals=model(SOURCE);
        assertEquals(1,goals.findActor("Performer").orElseThrow().associations().size());
        assertEquals(2,GoalModelValidator.validate(goals).size());
        assertTrue(GoalModelValidator.validate(goals,profile().validationPolicy()).isEmpty());
    }
    @Test void originalTaskAndAndDependencyPropagationRemainExecutable() {
        var cast=List.of(new ScenarioInstance("team","Team"),new ScenarioInstance("capability-definition","Capability"),new ScenarioInstance("alice","Performer"));
        try(var session=new Use4GoalRuntimeSessionAdapter(model(SOURCE),cast,(a,x,b,y)->true,"source-test",profile(),EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            String task=session.topology().occurrences().values().stream().filter(o->o.sourceElementId().equals("action")).findFirst().orElseThrow().occurrenceId();
            var result=session.step(new EngineInputs(Map.of(),Map.of(task,new EngineInputs.TaskInput("invocation",true,true,true)),Map.of()));
            var dependency=session.topology().instance().model().getDependencies().getFirst();
            assertEquals(GoalTaskStatus.FULFILLED,result.aggregate().goalTaskStatus(dependency.dependeeElmt()));
            assertEquals(GoalTaskStatus.FULFILLED,result.aggregate().goalTaskStatus(dependency.dependerElmt()));
            assertTrue(result.originalApiCalls().contains("TaskMarking.update"));
            assertTrue(result.originalApiCalls().contains("IStarPropagation.closePending"));
        }
    }
    @Test void unaryAdmissionDoesNotPermitArbitraryRefinementOrWrongKinds() {
        for(String invalid:List.of(SOURCE.replace("goal delivered : Achieve","goal delivered : Maintain"),
                SOURCE.replace("goal delivered : Achieve","goal delivered : Achieve condition {[ true ]}"),
                SOURCE.replace("task action > delivered pre {[ true ]} post {[ true ]}","goal action : Achieve > delivered")))
            assertFalse(new IStarFrontend().compile(invalid,"invalid",profile()).ok(),invalid);
        var wrong=model(SOURCE.replace("role Capability","agent Capability"));
        assertThrows(EngineAdmissionException.class,()->RuntimeModelAdmission.admit(wrong,List.of(new ScenarioInstance("team","Team"),new ScenarioInstance("base","Capability"),new ScenarioInstance("p","Performer")),(a,x,b,y)->true,profile()));
        assertFalse(new IStarFrontend().compile(SOURCE.replace("role Capability { }","role Capability { Capability is-a Performer }"),"cycle",profile()).ok());
    }
}
