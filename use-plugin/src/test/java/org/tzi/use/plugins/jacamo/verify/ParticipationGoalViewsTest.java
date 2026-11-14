package org.tzi.use.plugins.jacamo.verify;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.verify.api.*;
import org.tzi.use.plugins.jacamo.dsl.istar.parser.IStarFrontend;
import org.tzi.use.plugins.jacamo.verify.runtime.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;
import static org.junit.jupiter.api.Assertions.*;

class ParticipationGoalViewsTest {
    private static final EngineSemanticsProfile PROFILE=EngineSemanticsProfile.ROLE_SPECIALIZATION_V1;
    private static final String SOURCE="""
        istar Participation {
          agent Team { goal requested : Achieve }
          role Worker { goal start : Achieve }
          actor Capability {
            Capability participates-in Worker
            goal start : Achieve
            task action > start pre {[ true ]} post {[ true ]}
          }
          depend Team.requested -> goal delegation -> Worker.start
        }
        """;
    private GoalModel model(String text){return new IStarFrontend().compileScoped(text,"scoped.istar",PROFILE).requireModel();}
    @Test void participationSharesMatchingGoalActivationAndStatusWithoutAddingASocialDependency() {
        var source=model(SOURCE);String before=OwnedGoalModel.fingerprint(source);var views=new ParticipationGoalViews(source);
        assertEquals(1,source.getDependencies().size());assertEquals("Worker",source.getDependencies().getFirst().dependee());
        assertEquals("Capability.start",views.canonical("Worker.start"));
        assertEquals(1,views.executionModel().getDependencies().size());
        assertEquals("Capability",views.executionModel().getDependencies().getFirst().dependee());
        assertEquals("requested",GoalActivationGraph.of(views.executionModel()).parentOf("Capability.start").orElseThrow());
        var cast=source.getActors().stream().map(a->new ScenarioInstance(a.name()+"Instance",a.name())).toList();
        try(var session=new Use4GoalRuntimeSessionAdapter(views.executionModel(),cast,(a,x,b,y)->true,"shared-csl-context",PROFILE,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1)) {
            var action=session.topology().occurrences().values().stream().filter(o->o.sourceElementId().equals("action")).findFirst().orElseThrow();
            var requested=session.topology().occurrences().values().stream().filter(o->o.sourceElementId().equals("requested")).findFirst().orElseThrow();
            var first=session.step(new EngineInputs(Map.of(),Map.of(action.occurrenceId(),new EngineInputs.TaskInput("state",true,true,false)),Map.of()));
            assertNotEquals(GoalTaskStatus.FULFILLED,first.aggregate().goalTaskStatus(requested.occurrenceId()));
            var next=session.step(new EngineInputs(Map.of(),Map.of(action.occurrenceId(),new EngineInputs.TaskInput("state",true,true,true)),Map.of()));
            assertEquals(GoalTaskStatus.FULFILLED,next.aggregate().goalTaskStatus(requested.occurrenceId()));
        }
        assertEquals(Set.of("Capability.start","Worker.start"),new HashSet<>(views.viewsOf("Capability.start")));
        assertEquals(before,OwnedGoalModel.fingerprint(source));
    }
    @Test void equalNamesAloneDoNotCreateSharingAndTheDefaultCompilerKeepsGlobalIdentityRules() {
        assertFalse(new IStarFrontend().compile(SOURCE,"legacy.istar",PROFILE).ok());
        var independent=model(SOURCE.replace("Capability participates-in Worker",""));
        assertTrue(new ParticipationGoalViews(independent).aliases().isEmpty());
        assertFalse(new IStarFrontend().compileScoped(SOURCE.replace("goal start : Achieve\n", "goal start : Achieve goal start : Achieve\n"),"bad.istar",PROFILE).ok());
    }
    @Test void conflictingTypesAndMultipleProvidersRequireAnExplicitSemanticDecision() {
        var conflict=model(SOURCE.replace("role Worker { goal start : Achieve }","role Worker { goal start : Maintain }"));
        assertThrows(IllegalArgumentException.class,()->new ParticipationGoalViews(conflict));
        var multiple=model(SOURCE.replace("depend Team.requested", "actor Other { Other participates-in Worker goal start : Achieve } depend Team.requested"));
        assertThrows(IllegalArgumentException.class,()->new ParticipationGoalViews(multiple));
    }
}
