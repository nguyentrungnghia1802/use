package org.tzi.use.plugins.jacamo.verify;

import org.vnu.sme.goal.verify.istar.IStarMarking;
import org.vnu.sme.goal.verify.istar.IStarMarking.GoalTaskStatus;
import org.vnu.sme.goal.verify.istar.IStarMarking.TaskMarking;
import org.vnu.sme.goal.verify.istar.IStarMarking.GoalMarking;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tzi.use.plugins.jacamo.verify.api.*;
import org.vnu.sme.goal.dsl.istar.parser.IStarFrontend;
import org.tzi.use.plugins.jacamo.verify.oracle.PinnedOriginalOracle;
import org.tzi.use.plugins.jacamo.verify.istar.*;
import org.vnu.sme.goal.dsl.istar_marking.mm.*;
import org.vnu.sme.goal.dsl.istar_marking.parser.GoalModelInstanceTranslator;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.tzi.use.plugins.jacamo.verify.api.EngineDiagnostic.AdmissionException;
import org.tzi.use.plugins.jacamo.verify.istar.GoalTaskSession.Inputs;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.verify.oracle.PinnedOriginalOracle.*;

class OriginalRuntimeSessionTest {
    @Test void changedExecutionModelTransfersOnlyIdenticalLeafContractsAndExactEpisodes() {
        String source="istar Stable { role R { task t pre {[ true ]} post {[ true ]} } }";
        String expanded="istar Expanded { role R { task t pre {[ true ]} post {[ true ]} task sibling pre {[ false ]} post {[ true ]} } }";
        String changed="istar Changed { role R { task t pre {[ false ]} post {[ true ]} } }";
        try(var first=single(source);var next=single(expanded);var wrong=single(changed);var episode=single(expanded)) {
            String t=id(first,"t");first.step(new Inputs(Map.of(),Map.of(t,new Inputs.TaskInput("exact-episode",true,true,true))));
            assertThrows(IllegalArgumentException.class,()->next.seedStableHistory(first.checkpoint(),Map.of(t,"exact-episode")),"Existing whole-definition fence remains strict");
            next.seedCompatibleLeafHistory(first,Map.of(t,"exact-episode"));assertEquals(first.checkpoint().tasks(),next.checkpoint().tasks());
            assertTrue(next.checkpoint().goals().isEmpty());assertEquals(0,next.checkpoint().boundary());
            wrong.seedCompatibleLeafHistory(first,Map.of(t,"exact-episode"));assertTrue(wrong.checkpoint().tasks().isEmpty());
            episode.seedCompatibleLeafHistory(first,Map.of(t,"another-invocation"));assertTrue(episode.checkpoint().tasks().isEmpty());
            next.step(Inputs.empty());assertThrows(IllegalStateException.class,()->next.seedCompatibleLeafHistory(first,Map.of(t,"exact-episode")));
        }
    }
    @Test void topologyRebuildSeedsOnlyExactStableKeysAndEpisodesWithoutChangingOriginalRecords() {
        String source="istar Stable { role R { goal g : Achieve condition {[ true ]} } }";
        try(var first=single(source);var stable=single(source);var changed=single(source)) {
            String g=id(first,"g");var known=first.step(goals(Map.of(g,new Inputs.GoalInput("episode",true,true)))).history();
            stable.seedStableHistory(known,Map.of(g,"episode"));assertEquals(known.goals(),stable.checkpoint().goals());assertEquals(known.keys(),stable.checkpoint().keys());
            changed.seedStableHistory(known,Map.of(g,"new-episode"));assertTrue(changed.checkpoint().goals().isEmpty());
            stable.step(goals(Map.of(g,new Inputs.GoalInput("episode",true,false))));assertEquals(GoalTaskStatus.FULFILLED,stable.checkpoint().goals().get(g).status());assertThrows(IllegalStateException.class,()->stable.seedStableHistory(known,Map.of(g,"episode")));
        }
    }
    private static PinnedOriginalOracle original;
    @BeforeAll static void compileOriginal() throws Exception {original=new PinnedOriginalOracle();}
    @AfterAll static void closeOriginal() throws Exception {if(original!=null)original.close();}
    private GoalModel model(String source){return new IStarFrontend().compile(source,"runtime.istar").requireModel();}
    private GoalTaskSession single(String source) {
        return new GoalTaskSession(model(source),List.of(new ScenarioInstance("alice","R")),(a,x,b,y)->x.equals("alice")&&y.equals("alice"),"cast-1");
    }
    private static String id(GoalTaskSession session,String sourceId) {
        return session.topology().occurrences().values().stream().filter(b->b.sourceElementId().equals(sourceId)).map(b->b.occurrenceId()).findFirst().orElseThrow();
    }
    private static Inputs goals(Map<String,Inputs.GoalInput> input){return new Inputs(input,Map.of());}
    @ParameterizedTest @EnumSource(GoalType.class)
    void everyFourStepBooleanActivationTraceMatchesOriginalAllGoalFields(GoalType type) throws Exception {
        String label=switch(type){case ACHIEVE->"Achieve";case MAINTAIN->"Maintain";case SUSTAIN->"Sustain";case NONE->"None";};
        try(var session=single("istar Trace { role R { goal g : "+label+" condition {[ true ]} } }")) {
            String g=id(session,"g");
            for(int active=0;active<16;active++)for(int condition=0;condition<16;condition++) {
                session.reset();var trace=new ArrayList<boolean[]>();
                for(int step=0;step<4;step++)trace.add(new boolean[]{(active&(1<<step))!=0,(condition&(1<<step))!=0});
                var expected=original.goalTrace(type.name(),trace);
                for(int step=0;step<4;step++) {
                    boolean[] input=trace.get(step);var result=session.step(goals(Map.of(g,new Inputs.GoalInput("e",input[0],input[1]))));
                    assertEquals(expected.get(step),normalize(result.history().goals().get(g)),type+":"+active+":"+condition+":"+step);
                }
            }
        }
    }
    @Test void everyThreeStepTaskTupleTraceMatchesOriginalHistoricalFields() throws Exception {
        try(var session=single("istar Tasks { role R { task t pre {[ true ]} post {[ true ]} } }")) {
            String t=id(session,"t");
            for(int encoded=0;encoded<512;encoded++) {
                session.reset();var trace=new ArrayList<boolean[]>();int code=encoded;
                for(int step=0;step<3;step++){int tuple=code&7;code>>=3;trace.add(new boolean[]{(tuple&1)!=0,(tuple&2)!=0,(tuple&4)!=0});}
                var expected=original.taskTrace(trace);
                for(int step=0;step<3;step++) {
                    var tuple=trace.get(step);var result=session.step(new Inputs(Map.of(),Map.of(t,new Inputs.TaskInput("e",tuple[0],tuple[1],tuple[2]))));
                    assertEquals(expected.get(step),normalize(result.history().tasks().get(t)),"task:"+encoded+":"+step);
                }
            }
        }
    }
    @Test void unknownAndPreOnlyDeferWithoutFalseInputOrLeafHistoryLoss() {
        try(var session=single("istar Unknown { role R { goal g : Maintain condition {[ true ]} task t pre {[ true ]} post {[ true ]} } }")) {
            String g=id(session,"g"),t=id(session,"t");
            var known=session.step(goals(Map.of(g,new Inputs.GoalInput("e",true,true))));
            var unknown=session.step(goals(Map.of(g,new Inputs.GoalInput("e",true,null))));
            assertEquals(known.history().goals(),unknown.history().goals());assertTrue(unknown.deferred().contains(g));
            assertFalse(unknown.originalApiCalls().contains("GoalMarking.update"));
            var pre=session.step(new Inputs(Map.of(),Map.of(t,new Inputs.TaskInput("e",true,true,null))));
            assertEquals(TaskMarking.initial(),pre.history().tasks().get(t));assertTrue(pre.deferred().contains(t));
            assertFalse(pre.originalApiCalls().contains("TaskMarking.update"));
            var complete=session.step(new Inputs(Map.of(),Map.of(t,new Inputs.TaskInput("e",true,true,true))));
            assertEquals(GoalTaskStatus.FULFILLED,complete.history().tasks().get(t).status());
            var notRequired=session.step(new Inputs(Map.of(),Map.of(t,new Inputs.TaskInput("e",false,null,null))));
            assertEquals(complete.history().tasks(),notRequired.history().tasks());
            assertEquals(GoalTaskStatus.FULFILLED,unknown.history().goals().get(g).status(),"Raw last-known record is preserved; the deferred diagnostic is separate");
        }
    }
    @Test void episodeSavepointAtomicFailureResetAndOwnerFencesPreventHistoryTransfer() {
        String source="istar Ownership { role R { goal g : Achieve condition {[ true ]} } }";
        try(var session=single(source);var another=single(source)) {
            String g=id(session,"g");var initial=session.checkpoint();
            session.step(goals(Map.of(g,new Inputs.GoalInput("first",true,true))));var fulfilled=session.checkpoint();
            session.restore(initial);assertEquals(initial,session.checkpoint());
            session.restore(fulfilled);
            var fresh=session.step(goals(Map.of(g,new Inputs.GoalInput("second",true,false))));assertEquals(GoalTaskStatus.PENDING,fresh.history().goals().get(g).status());
            var before=session.checkpoint();
            assertThrows(IllegalArgumentException.class,()->session.step(goals(Map.of("missing",new Inputs.GoalInput("e",true,true)))));
            assertEquals(before,session.checkpoint());
            assertThrows(IllegalArgumentException.class,()->another.restore(before));
            session.reset();assertThrows(IllegalArgumentException.class,()->session.restore(before));assertTrue(session.checkpoint().goals().isEmpty());
            session.close();assertThrows(IllegalStateException.class,()->session.step(Inputs.empty()));
        }
    }
    @Test void originalAndOrAreCalledOnDerivedCopies() throws Exception {
        for(boolean or:new boolean[]{false,true}) {
            String source="istar Refine { role R { goal parent goal a : Achieve > "+(or?"or ":"")+"parent condition {[ true ]} goal b : Achieve > "+(or?"or ":"")+"parent condition {[ true ]} } }";
            try(var session=single(source)) {
                String a=id(session,"a"),b=id(session,"b"),parent=id(session,"parent");
                var result=session.step(goals(Map.of(a,new Inputs.GoalInput("e",true,true),b,new Inputs.GoalInput("e",true,false))));
                assertEquals(or?GoalTaskStatus.FULFILLED:GoalTaskStatus.PENDING,result.aggregate().goalTaskStatus(parent));
                var reference=original.scenario(source,Map.of("alice","R"),Map.of("alice","ctx"),List.of(new String[]{"alice","a","Fulfilled"},new String[]{"alice","b","Pending"}));
                assertGoalTaskMarkings(property(reference,"instanceMarking"),result.aggregate());
                assertEquals(GoalMarking.initial(GoalType.ACHIEVE).update(true,true),result.history().goals().get(a));
            }
        }
    }

    @Test void qualityDeclarationsRemainReadableButQualityVerificationIsRejected() {
        var cast = List.of(new ScenarioInstance("alice", "R"));
        String declaration = "istar Example { role R { quality q goal g : Achieve condition {[ true ]} } }";
        try (var session = single(declaration)) {
            String goal = id(session, "g");
            var result = session.step(goals(Map.of(goal, new Inputs.GoalInput("e", true, true))));
            assertEquals(Map.of(goal, GoalTaskStatus.FULFILLED), result.aggregate().goalTaskStatuses());
        }
        for (String contribution : List.of("make", "break")) {
            String source = "istar Example { role R { quality q goal g : Achieve > " + contribution
                    + " q condition {[ true ]} } }";
            var error = assertThrows(AdmissionException.class, () -> single(source));
            assertTrue(error.diagnostics().stream().anyMatch(d -> d.code().equals("QUALITY_CONTRIBUTION_NOT_VERIFIED")));
            var noAssignments = new IStarScenarioModel("trace", "<in-memory>", cast, List.of());
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> GoalModelInstanceTranslator.evaluate(model(source), noAssignments))
                    .getMessage().startsWith("QUALITY_CONTRIBUTION_NOT_VERIFIED:"));
        }
        var scenario = new IStarScenarioModel("trace", "<in-memory>", cast,
                List.of(new ScenarioStmt.Assign("alice", "q", "True")));
        assertEquals("QUALITY_STATUS_NOT_VERIFIED:q", assertThrows(IllegalArgumentException.class,
                () -> GoalModelInstanceTranslator.evaluate(model(declaration), scenario)).getMessage());
    }

    private static void assertGoalTaskMarkings(Object originalMarking, IStarMarking actual) throws Exception {
        assertEquals(normalize(property(originalMarking, "goalMarkings")), normalize(actual.goalMarkings()));
        assertEquals(normalize(property(originalMarking, "taskMarkings")), normalize(actual.taskMarkings()));
    }

    @Test void contextualSeamMatchesOriginalAndKeepsStandaloneAndBoundOccurrencesDistinct() throws Exception {
        String source="istar Dependency { role Worker { goal supplied : Achieve condition {[ true ]} } role Reviewer { goal requested : Achieve } depend Reviewer.requested -> goal promise -> Worker.supplied }";
        var cast=new LinkedHashMap<String,String>();cast.put("alice","Worker");cast.put("bob","Reviewer");
        Map<String,String> contexts=Map.of("alice","team","bob","team");
        try(var session=new GoalTaskSession(model(source),cast.entrySet().stream().map(e->new ScenarioInstance(e.getKey(),e.getValue())).toList(),
                (at,a,bt,b)->Objects.equals(contexts.get(a),contexts.get(b)),"cast")) {
            var originalEmpty=original.scenario(source,cast,contexts,List.of());
            assertEquals(normalize(property(originalEmpty,"goalModelInstance")),normalize(session.topology().instance()));
            String supply=id(session,"supplied");var result=session.step(goals(Map.of(supply,new Inputs.GoalInput("e",true,true))));
            var dependency=session.topology().instance().model().getDependencies().getFirst();
            assertEquals(GoalTaskStatus.FULFILLED,result.aggregate().goalTaskStatus(dependency.dependerElmt()));
            var requested=session.topology().occurrences().values().stream().filter(b->b.sourceElementId().equals("requested")).toList();assertEquals(2,requested.size());
            var standalone=requested.stream().filter(b->!b.occurrenceId().equals(dependency.dependerElmt())).findFirst().orElseThrow();
            assertEquals(GoalTaskStatus.UNKNOWN,result.aggregate().goalTaskStatus(standalone.occurrenceId()));
            assertEquals(1,standalone.stack().size());assertEquals(2,session.topology().occurrences().get(dependency.dependerElmt()).stack().size());
            var reference=original.scenario(source,cast,contexts,Collections.singletonList(new String[]{"alice","supplied","Fulfilled"}));
            assertGoalTaskMarkings(property(reference,"instanceMarking"),result.aggregate());
            assertTrue(result.originalApiCalls().contains("GoalModelInstanceTranslator.propagateDependencies"));
            Path evidence=Path.of("target/oracle/contextual-parity.txt");Files.createDirectories(evidence.getParent());Files.writeString(evidence,normalize(result.aggregate().goalTaskStatuses())+"\n"+normalize(property(property(reference,"instanceMarking"),"goalTaskStatuses")));
        }
    }
    @Test void unsupportedGraphsCannotEnterSilentCapOrNonrepresentableStatusDomains() {
        for(String source:List.of(
                "istar Bad { role R { goal parent : Maintain goal a : None > parent goal b : None > parent } }",
                "istar Bad { role R { task parent goal a : Maintain > parent goal b : Maintain > parent } }",
                "istar Bad { role R { goal parent : Achieve goal a : Maintain > parent goal b : Maintain > parent } }",
                "istar Bad { role R { quality q goal a > make q goal b > break q } }",
                "istar Bad { role R { quality q goal a > help q } }")) {
            var error=assertThrows(AdmissionException.class,()->single(source));
            assertTrue(error.diagnostics().stream().anyMatch(d->d.kind()==EngineDiagnostic.Kind.UNSUPPORTED));
        }
        String source="istar Cycle { role A { goal a } role B { goal b } depend A.a -> goal d1 -> B.b depend B.b -> goal d2 -> A.a }";
        assertThrows(AdmissionException.class,()->new GoalTaskSession(model(source),List.of(new ScenarioInstance("a","A"),new ScenarioInstance("b","B")),(a,x,b,y)->true,"cast"));
        assertEquals(GoalTaskStatus.VIOLATED,GoalMarking.fromStatus(GoalType.MAINTAIN,GoalTaskStatus.PENDING).status());
        assertEquals(GoalTaskStatus.PENDING,GoalMarking.fromStatus(GoalType.ACHIEVE,GoalTaskStatus.VIOLATED).status());
        assertThrows(IllegalArgumentException.class,()->TaskMarking.fromStatus(GoalTaskStatus.VIOLATED));
    }
    @Test void originalGoalTaskScenarioResultsMatchWithSharedAndSeparateContexts() throws Exception {
        String source="istar Dependency { role Worker { goal supplied : Achieve condition {[ true ]} } role Reviewer { goal requested : Achieve } depend Reviewer.requested -> goal promise -> Worker.supplied }";
        var cast=new LinkedHashMap<String,String>();cast.put("alice","Worker");cast.put("bob","Reviewer");
        var instances=cast.entrySet().stream().map(e->new ScenarioInstance(e.getKey(),e.getValue())).toList();
        for(var contexts:List.of(Map.of("alice","team","bob","team"),Map.of("alice","one","bob","two"))) {
            for(boolean assign:new boolean[]{false,true}) {
                List<ScenarioStmt> statements=assign?List.of(new ScenarioStmt.Assign("alice","supplied","Fulfilled")):List.of();
                var scenario=new IStarScenarioModel("live","<in-memory>",instances,statements);
                var actual=GoalModelInstanceTranslator.evaluate(model(source),scenario,(a,x,b,y)->Objects.equals(contexts.get(x),contexts.get(y)));
                var expected=original.scenario(source,cast,contexts,assign?Collections.singletonList(new String[]{"alice","supplied","Fulfilled"}):List.of());
                assertGoalTaskMarkings(property(expected, "instanceMarking"), actual.instanceMarking());
                for (String field : List.of("goalModelInstance", "aggregates", "actorLabels",
                        "nodeLabels", "elementScopeActorType"))
                    assertEquals(normalize(property(expected, field)), normalize(property(actual, field)), field);
                if(!assign)assertEquals(normalize(property(expected,"goalModelInstance")),normalize(GoalModelInstanceTranslator.topology(model(source),scenario,(a,x,b,y)->Objects.equals(contexts.get(x),contexts.get(y))).instance()));
            }
        }
    }
    @Test void castContextAmbiguitySizeAndRecursiveDepthHaveExplicitAdmissionFailures() {
        String source="istar Dependency { role A { goal a } role B { goal b } depend A.a -> goal promise -> B.b }";
        var definition=model(source);
        assertThrows(AdmissionException.class,()->new GoalTaskSession(definition,List.of(new ScenarioInstance("a","A"),new ScenarioInstance("b","B")),(at,a,bt,b)->a.equals(b),"cast"));
        assertThrows(AdmissionException.class,()->new GoalTaskSession(definition,List.of(new ScenarioInstance("a","A"),new ScenarioInstance("b","B"),new ScenarioInstance("c","B")),(at,a,bt,b)->true,"cast"));
        var huge=new GoalModel("Huge");var goals=new ArrayList<IntentionalElement>();for(int i=0;i<4097;i++)goals.add(new Goal("g"+i,GoalType.ACHIEVE));
        huge.addActor(new Role("R",goals,List.of(),List.of(),List.of(),List.of(),List.of()));
        assertThrows(AdmissionException.class,()->new GoalTaskSession(huge,List.of(new ScenarioInstance("a","R")),(a,x,b,y)->true,"cast"));
        var text=new StringBuilder("istar Deep { role R { goal g0 ");for(int i=1;i<260;i++)text.append("goal g").append(i).append(" > or g").append(i-1).append(' ');text.append("} }");
        var result=new IStarFrontend().compile(text.toString(),"depth.istar");assertFalse(result.ok());assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals("ENGINE_GRAPH_DEPTH_LIMIT")));
        var deep=new GoalModel("ApiDeep");var nodes=new ArrayList<IntentionalElement>();var refinements=new ArrayList<Refinement>();
        for(int i=0;i<260;i++){nodes.add(new Goal("n"+i,GoalType.NONE));if(i>0)refinements.add(new OrRefinement("n"+(i-1),"n"+i));}
        deep.addActor(new Role("R",nodes,refinements,List.of(),List.of(),List.of(),List.of()));
        var error=assertThrows(AdmissionException.class,()->new GoalTaskSession(deep,List.of(new ScenarioInstance("a","R")),(a,x,b,y)->true,"cast"));
        assertTrue(error.diagnostics().stream().anyMatch(d->d.code().equals("ENGINE_GRAPH_DEPTH_LIMIT")));
    }
    @Test void callerMutationOfSourceOrInstantiatedDefinitionFailsBeforeHistoryCommit() {
        try(var session=single("istar Mutable { role R { goal g condition {[ true ]} } }")) {
            var checkpoint=session.checkpoint();var instantiated=session.topology().instance().model();
            ((Goal)instantiated.allElements().values().iterator().next()).setCondition(new IStarOclConstraint("false"));
            assertThrows(IllegalStateException.class,()->session.step(Inputs.empty()));
            assertEquals(checkpoint,session.checkpoint());
        }
    }
}
