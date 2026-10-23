package org.tzi.use.semantic.engine;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.tzi.use.semantic.engine.runtime.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.istar.parser.IStarCompiler;
import org.vnu.sme.goal.verify.conformance.semantics.*;
import static org.junit.jupiter.api.Assertions.*;

class DerivedDependeePolicyTest {
    private static final EngineSemanticsProfile PROFILE = EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1;
    private static final String SOURCE = """
        istar Supply {
          agent Context { goal requested : Achieve }
          role Worker {
            goal provided : Achieve
            task first > provided pre {[ true ]} post {[ true ]}
            task second > provided pre {[ true ]} post {[ true ]}
            resource tool > needed-by second
            Worker participates-in Context
          }
          depend Context.requested -> goal promise -> Worker.provided
        }
        """;
    private static final List<ScenarioInstance> CAST = List.of(new ScenarioInstance("scope", "Context"), new ScenarioInstance("alice", "Worker"));
    private static String id(Use4GoalRuntimeSessionAdapter session, String source) {
        return session.topology().occurrences().values().stream().filter(b -> b.sourceElementId().equals(source)).findFirst().orElseThrow().occurrenceId();
    }
    private static GoalModel model(String source) { return new IStarFrontend().compile(source, "policy.istar", PROFILE).requireModel(); }
    private static Use4GoalRuntimeSessionAdapter session() {
        return new Use4GoalRuntimeSessionAdapter(model(SOURCE), CAST, (a,x,b,y) -> true, "cast", PROFILE);
    }
    @Test void strictDefaultsStillRejectWhileEveryOptInValidationLayerAgrees() {
        assertEquals(1, IStarCompiler.compile(SOURCE).errors().size());
        var result = new IStarFrontend().compile(SOURCE, "policy.istar", PROFILE);
        assertTrue(result.ok(), result.diagnostics().toString());
        assertTrue(GoalModelValidator.validate(result.model(), PROFILE.validationPolicy()).isEmpty());
        assertEquals(1, GoalModelValidator.validate(result.model()).size());
        assertThrows(EngineAdmissionException.class, () -> new OwnedGoalModel(result.model()));
        assertEquals(result.takeOwnership().hash(), new OwnedGoalModel(result.model(), PROFILE).hash());
        assertThrows(EngineAdmissionException.class, () -> new Use4GoalRuntimeSessionAdapter(result.model(), CAST, (a,x,b,y)->true, "strict"));
        try (var session = session()) {
            assertEquals(PROFILE, session.profile());
            assertFalse(session.topology().instance().model().getDependencies().isEmpty());
        }
    }
    @Test void optInStillRejectsOrConditionNonleafDependerCyclesAndForeignOwnership() {
        for (String invalid : List.of(
                SOURCE.replace("> provided", "> or provided"),
                SOURCE.replace("goal provided : Achieve", "goal provided : Achieve condition {[ true ]}"),
                SOURCE.replace("goal requested : Achieve", "goal requested : Achieve task extra1 > requested task extra2 > requested"),
                SOURCE.replace("task first > provided", "task first > requested"),
                SOURCE.replace("depend Context.requested", "depend Worker.provided -> goal back -> Context.requested depend Context.requested"))) {
            assertFalse(new IStarFrontend().compile(invalid, "invalid", PROFILE).ok(), invalid);
        }
        assertFalse(new IStarFrontend().compile("istar Invalid { role R { goal g activation {[ true ]} } }", "activation", PROFILE).ok());
        var wrong = model(SOURCE.replace("Worker participates-in Context", "Worker is-a Context"));
        assertThrows(EngineAdmissionException.class, () -> new Use4GoalRuntimeSessionAdapter(wrong, CAST, (a,x,b,y)->true, "cast", PROFILE));
        assertThrows(EngineAdmissionException.class, () -> new Use4GoalRuntimeSessionAdapter(model(SOURCE), CAST, (a,x,b,y)->x.equals(y), "cast", PROFILE));
    }
    @Test void missingOrForeignResourceWitnessCannotUpdateOrCreditTaskAndFailureIsAtomic() {
        try (var session = session()) {
            String second = id(session, "second");
            var task = Map.of(second, new EngineInputs.TaskInput("episode", true, true, true));
            var absent = session.step(new EngineInputs(Map.of(), task, Map.of()));
            assertTrue(absent.deferred().contains(second));
            assertEquals(TaskMarking.initial(), absent.history().tasks().get(second));
            assertFalse(absent.originalApiCalls().contains("TaskMarking.update"));
            var key = new EngineInputs.ResourceKey(second, "tool");
            for (var value : List.of(new EngineInputs.ResourceInput("episode", false, "observed-absent"),
                    new EngineInputs.ResourceInput("other-episode", true, "artifact-uuid"),
                    new EngineInputs.ResourceInput("episode", null, "partial"))) {
                var deferred = session.step(new EngineInputs(Map.of(), task, Map.of(), Map.of(key, value)));
                assertTrue(deferred.deferred().contains(second));
                assertEquals(TaskMarking.initial(), deferred.history().tasks().get(second));
            }
            assertThrows(IllegalArgumentException.class, () -> new EngineInputs.ResourceInput("episode", true, ""));
            var before = session.checkpoint();
            assertThrows(IllegalArgumentException.class, () -> session.step(new EngineInputs(Map.of(), task, Map.of(), Map.of(
                    new EngineInputs.ResourceKey(second, "not-tool"), new EngineInputs.ResourceInput("episode", true, "uuid")))));
            assertEquals(before, session.checkpoint());
            var ready = session.step(new EngineInputs(Map.of(), task, Map.of(), Map.of(key, new EngineInputs.ResourceInput("episode", true, "artifact-uuid"))));
            assertEquals(GoalTaskStatus.FULFILLED, ready.history().tasks().get(second).status());
            assertTrue(ready.originalApiCalls().contains("TaskMarking.update"));
        }
    }
    @Test void originalAndAndDependencyPropagationRequireBothTasksAndExplicitResourceInput() {
        try (var session = session()) {
            var tasks = new LinkedHashMap<String, EngineInputs.TaskInput>();
            session.topology().occurrences().values().stream().filter(b -> Set.of("first","second").contains(b.sourceElementId()))
                    .forEach(b -> tasks.put(b.occurrenceId(), new EngineInputs.TaskInput("e", true, true, true)));
            var resources = new LinkedHashMap<EngineInputs.ResourceKey, EngineInputs.ResourceInput>();
            tasks.keySet().stream().filter(k -> session.topology().occurrences().get(k).sourceElementId().equals("second"))
                    .forEach(k -> resources.put(new EngineInputs.ResourceKey(k, "tool"), new EngineInputs.ResourceInput("e", true, "same-context-artifact")));
            var result = session.step(new EngineInputs(Map.of(), tasks, Map.of(), resources));
            for (var dependency : session.topology().instance().model().getDependencies()) {
                assertEquals(GoalTaskStatus.FULFILLED, result.aggregate().goalTaskStatus(dependency.dependeeElmt()));
                assertEquals(GoalTaskStatus.FULFILLED, result.aggregate().goalTaskStatus(dependency.dependerElmt()));
            }
            assertTrue(result.originalApiCalls().contains("IStarPropagation.closePending"));
            assertTrue(result.originalApiCalls().contains("GoalModelInstanceTranslator.propagateDependencies"));
        }
    }
    @Test void policyOwnsHistoryWhileStableOccurrenceSeedingSurvivesACastRevision() {
        String source="istar Stable { role R { goal g : Achieve condition {[ true ]} } }";
        var model=new IStarFrontend().compile(source,"stable").requireModel();
        var cast=List.of(new ScenarioInstance("alice","R"));
        try(var strict=new Use4GoalRuntimeSessionAdapter(model,cast,(a,x,b,y)->true,"one");
            var extended=new Use4GoalRuntimeSessionAdapter(model,cast,(a,x,b,y)->true,"one",PROFILE);
            var rebuilt=new Use4GoalRuntimeSessionAdapter(model,cast,(a,x,b,y)->true,"two",PROFILE)) {
            String id=id(extended,"g");
            var history=extended.step(new EngineInputs(Map.of(id,new EngineInputs.GoalInput("e",true,true)),Map.of(),Map.of())).history();
            assertThrows(IllegalArgumentException.class,()->strict.seedStableHistory(history,Map.of(id,"e")));
            rebuilt.seedStableHistory(history,Map.of(id,"e"));
            assertEquals(history.goals(),rebuilt.checkpoint().goals());
        }
    }
}
