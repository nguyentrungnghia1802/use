package org.tzi.use.plugins.jacamo.verify;

import org.tzi.use.plugins.jacamo.verify.istar.GoalTaskSession.TopologyPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.verify.api.EngineSemanticsProfile;
import org.vnu.sme.goal.dsl.istar.parser.IStarFrontend;
import org.tzi.use.plugins.jacamo.verify.istar.*;
import org.vnu.sme.goal.dsl.istar_marking.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.verify.istar.IStarMarking.GoalTaskStatus;
import org.tzi.use.plugins.jacamo.verify.istar.GoalTaskSession.Inputs;
import static org.junit.jupiter.api.Assertions.*;

class GenericActorTest {
    private static final EngineSemanticsProfile PROFILE=EngineSemanticsProfile.ROLE_SPECIALIZATION_V1;
    private static final String SOURCE="""
        istar GenericView {
          agent Organization { goal requested : Achieve }
          role Performer { goal responsibility : Achieve }
          role Observer { }
          actor Capability {
            goal supplied : Achieve
            task action > supplied pre {[ true ]} post {[ true ]}
          }
          depend Organization.requested -> goal delegation -> Performer.responsibility
          depend Performer.responsibility -> goal implementation -> Capability.supplied
        }
        """;
    @Test void syntaxPreservesOneAgentTwoRolesAndAnUnspecifiedActorThroughBothTopologies() {
        var model=new IStarFrontend().compile(SOURCE,"generic.istar",PROFILE).requireModel();
        assertEquals(1,model.getActors().stream().filter(Agent.class::isInstance).count());
        assertEquals(2,model.getActors().stream().filter(Role.class::isInstance).count());
        assertInstanceOf(GenericActor.class,model.findActor("Capability").orElseThrow());
        var cast=model.getActors().stream().map(a->new ScenarioInstance("instance_"+a.name(),a.name())).toList();
        for(var policy:TopologyPolicy.values())try(var session=new GoalTaskSession(model,cast,(a,x,b,y)->true,"one-state-context",PROFILE,policy)) {
            assertTrue(session.topology().instance().model().getActors().stream().anyMatch(GenericActor.class::isInstance));
            if(policy!=TopologyPolicy.SINGLE_OWNER_CONTEXT_V1)continue;
            var tasks=new HashMap<String,Inputs.TaskInput>();
            session.topology().occurrences().values().stream().filter(o->o.sourceElementId().equals("action")).forEach(o->tasks.put(o.occurrenceId(),new Inputs.TaskInput("state",true,true,true)));
            var result=session.step(new Inputs(Map.of(),tasks));
            var requested=session.topology().occurrences().values().stream().filter(o->o.sourceElementId().equals("requested")).findFirst().orElseThrow();
            assertEquals(GoalTaskStatus.FULFILLED,result.aggregate().goalTaskStatus(requested.occurrenceId()));
        }
    }
}
